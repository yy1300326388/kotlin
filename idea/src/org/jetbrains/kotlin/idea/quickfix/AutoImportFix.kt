/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithVisibility
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.DiagnosticWithParameters2
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.JetBundle
import org.jetbrains.kotlin.idea.actions.KotlinAddImportAction
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.getResolveScope
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.KotlinIndicesHelper
import org.jetbrains.kotlin.idea.core.getResolutionScope
import org.jetbrains.kotlin.idea.core.isVisible
import org.jetbrains.kotlin.idea.project.ProjectStructureUtil
import org.jetbrains.kotlin.idea.util.CallTypeAndReceiver
import org.jetbrains.kotlin.lexer.JetTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isImportDirectiveExpression
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.utils.CachedValueProperty
import org.jetbrains.kotlin.utils.addToStdlib.singletonOrEmptyList
import java.util.*

/**
 * Check possibility and perform fix for unresolved references.
 */
abstract class AutoImportFixBase public constructor(
        expression: JetExpression,
        val diagnostics: Collection<Diagnostic> = emptyList()): JetHintAction<JetExpression>(expression), HighPriorityAction {

    protected constructor(
            expression: JetExpression,
            diagnostic: Diagnostic? = null) : this(expression, diagnostic.singletonOrEmptyList())

    private val modificationCountOnCreate = PsiModificationTracker.SERVICE.getInstance(element.getProject()).getModificationCount()

    @Volatile private var anySuggestionFound: Boolean? = null

    public val suggestions: Collection<DeclarationDescriptor> by CachedValueProperty(
            {
                val descriptors = computeSuggestions()
                anySuggestionFound = !descriptors.isEmpty()
                descriptors
            },
            { PsiModificationTracker.SERVICE.getInstance(element.getProject()).getModificationCount() })

    override fun showHint(editor: Editor): Boolean {
        if (!element.isValid() || isOutdated()) return false

        if (ApplicationManager.getApplication().isUnitTestMode() && HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true)) return false

        if (suggestions.isEmpty()) return false

        val addImportAction = createAction(element.project, editor)
        val hintText = ShowAutoImportPass.getMessage(suggestions.size > 1, addImportAction.highestPriorityFqName.asString())
        HintManager.getInstance().showQuestionHint(editor, hintText, element.getTextOffset(), element.getTextRange()!!.getEndOffset(), addImportAction)

        return true
    }

    override fun getText() = JetBundle.message("import.fix")

    override fun getFamilyName() = JetBundle.message("import.fix")

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile)
            = (super.isAvailable(project, editor, file)) && (anySuggestionFound ?: !suggestions.isEmpty())

    override fun invoke(project: Project, editor: Editor?, file: JetFile) {
        CommandProcessor.getInstance().runUndoTransparentAction {
            createAction(project, editor!!).execute()
        }
    }

    override fun startInWriteAction() = true

    private fun isOutdated() = modificationCountOnCreate != PsiModificationTracker.SERVICE.getInstance(element.getProject()).getModificationCount()

    protected open fun createAction(project: Project, editor: Editor) = KotlinAddImportAction(project, editor, element, suggestions)

    protected fun computeSuggestions(): Collection<DeclarationDescriptor> {
        if (!element.isValid()) return listOf()

        val file = element.getContainingFile() as? JetFile ?: return emptyList()

        val callTypeAndReceiver = getCallTypeAndReceiver()

        if (callTypeAndReceiver is CallTypeAndReceiver.UNKNOWN) return emptyList()

        var referenceNames = getImportNames(diagnostics, element).filter { it.isNotEmpty() }
        if (referenceNames.isEmpty()) return emptyList()

        return referenceNames.flatMapTo(LinkedHashSet()) {
            Helper.computeSuggestionsForName(callTypeAndReceiver, element, file, it, getSupportedErrors())
        }
    }

    protected abstract fun getSupportedErrors(): Collection<DiagnosticFactory<*>>
    protected abstract fun getCallTypeAndReceiver(): CallTypeAndReceiver<*, *>
    protected abstract fun getImportNames(diagnostics: Collection<Diagnostic>, element: JetExpression): Collection<String>

    private object Helper {
        public fun computeSuggestionsForName(
                callTypeAndReceiver: CallTypeAndReceiver<out JetElement?, *>,
                expression: JetExpression, file: JetFile, referenceName: String,
                supportedErrors: Collection<DiagnosticFactory<*>>): Collection<DeclarationDescriptor> {
            fun filterByCallType(descriptor: DeclarationDescriptor) = callTypeAndReceiver.callType.descriptorKindFilter.accepts(descriptor)

            val searchScope = getResolveScope(file)

            val bindingContext = expression.analyze(BodyResolveMode.PARTIAL)

            val diagnostics = bindingContext.getDiagnostics().forElement(expression)

            if (!diagnostics.any { it.getFactory() in supportedErrors }) return emptyList()

            val resolutionScope = expression.getResolutionScope(bindingContext, file.getResolutionFacade())
            val containingDescriptor = resolutionScope.ownerDescriptor

            fun isVisible(descriptor: DeclarationDescriptor): Boolean {
                if (descriptor is DeclarationDescriptorWithVisibility) {
                    return descriptor.isVisible(containingDescriptor, bindingContext, expression as? JetSimpleNameExpression)
                }

                return true
            }

            val result = ArrayList<DeclarationDescriptor>()

            val indicesHelper = KotlinIndicesHelper(expression.getResolutionFacade(), searchScope, ::isVisible, true)

            if (expression is JetSimpleNameExpression) {
                if (!expression.isImportDirectiveExpression() && !JetPsiUtil.isSelectorInQualified(expression)) {
                    if (ProjectStructureUtil.isJsKotlinModule(file)) {
                        indicesHelper.getKotlinClasses({ it == referenceName }, { true }).filterTo(result, ::filterByCallType)

                    }
                    else {
                        indicesHelper.getJvmClassesByName(referenceName).filterTo(result, ::filterByCallType)
                    }

                    indicesHelper.getTopLevelCallablesByName(referenceName).filterTo(result, ::filterByCallType)
                }
            }

            result.addAll(indicesHelper.getCallableTopLevelExtensions({ it == referenceName }, callTypeAndReceiver, expression, bindingContext))

            return if (result.size > 1)
                reduceCandidatesBasedOnDependencyRuleViolation(result, file)
            else
                result
        }

        private fun reduceCandidatesBasedOnDependencyRuleViolation(
                candidates: Collection<DeclarationDescriptor>, file: PsiFile): Collection<DeclarationDescriptor> {
            val project = file.project
            val validationManager = DependencyValidationManager.getInstance(project)
            return candidates.filter {
                val targetFile = DescriptorToSourceUtilsIde.getAnyDeclaration(project, it)?.containingFile ?: return@filter true
                validationManager.getViolatorDependencyRules(file, targetFile).isEmpty()
            }
        }
    }
}

class AutoImportFix(expression: JetSimpleNameExpression, diagnostic: Diagnostic? = null) : AutoImportFixBase(expression, diagnostic) {
    override fun getCallTypeAndReceiver() = CallTypeAndReceiver.detect(element as JetSimpleNameExpression)

    override fun getImportNames(diagnostics: Collection<Diagnostic>, element: JetExpression): Collection<String> {
        element as JetSimpleNameExpression

        if (element.getIdentifier() == null) {
            val conventionName = JetPsiUtil.getConventionName(element)
            if (conventionName != null) {
                if (element is JetOperationReferenceExpression) {
                    val elementType = element.firstChild.node.elementType
                    if (OperatorConventions.ASSIGNMENT_OPERATIONS.containsKey(elementType)) {
                        val conterpart = OperatorConventions.ASSIGNMENT_OPERATION_COUNTERPARTS.get(elementType)
                        val counterpartName = OperatorConventions.BINARY_OPERATION_NAMES.get(conterpart)
                        if (counterpartName != null) {
                            return listOf(conventionName.asString(), counterpartName.asString())
                        }
                    }
                }

                return listOf(conventionName.asString())
            }
        }
        else {
            return listOf(element.getReferencedName())
        }

        return emptyList()
    }

    override fun getSupportedErrors() = ERRORS

    companion object : JetSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): JetIntentionAction<JetExpression>? {
            return (diagnostic.getPsiElement() as? JetSimpleNameExpression)?.let { AutoImportFix(it, diagnostic) }
        }

        override fun isApplicableForCodeFragment() = true

        private val ERRORS: Collection<DiagnosticFactory<*>> by lazy(LazyThreadSafetyMode.PUBLICATION) { QuickFixes.getInstance().getDiagnostics(this) }
    }
}

class MissingInvokeAutoImportFix(expression: JetExpression, diagnostic: Diagnostic) : AutoImportFixBase(expression, diagnostic) {
    override fun getImportNames(diagnostics: Collection<Diagnostic>, element: JetExpression) = setOf("invoke")

    override fun getCallTypeAndReceiver() = CallTypeAndReceiver.OPERATOR(element)

    override fun getSupportedErrors() = ERRORS

    companion object : JetSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): JetIntentionAction<JetExpression>? {
            val element = diagnostic.psiElement
            if (element is JetExpression) {
                return MissingInvokeAutoImportFix(element, diagnostic)
            }

            return null
        }

        private val ERRORS by lazy(LazyThreadSafetyMode.PUBLICATION) { QuickFixes.getInstance().getDiagnostics(this) }
    }
}

class MissingArrayAccessorAutoImportFix(element: JetArrayAccessExpression, diagnostic: Diagnostic) : AutoImportFixBase(element, diagnostic) {
    override fun getImportNames(diagnostics: Collection<Diagnostic>, element: JetExpression): Set<String> {
        val s = if ((element.parent as? JetBinaryExpression)?.operationToken == JetTokens.EQ) "set" else "get"
        return setOf(s)
    }

    override fun getCallTypeAndReceiver() =
            CallTypeAndReceiver.OPERATOR((element as JetArrayAccessExpression).arrayExpression!!)

    override fun getSupportedErrors() = ERRORS

    companion object : JetSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): JetIntentionAction<JetExpression>? {
            val element = diagnostic.psiElement
            if (element is JetArrayAccessExpression && element.arrayExpression != null) {
                return MissingArrayAccessorAutoImportFix(element, diagnostic)
            }

            return null
        }

        private val ERRORS by lazy(LazyThreadSafetyMode.PUBLICATION) { QuickFixes.getInstance().getDiagnostics(this) }
    }
}

class MissingDelegateAccessorsAutoImportFix(element: JetExpression, diagnostics: Collection<Diagnostic>) : AutoImportFixBase(element, diagnostics) {
    override fun createAction(project: Project, editor: Editor): KotlinAddImportAction {
        return KotlinAddImportAction(project, editor, element, suggestions)
    }

    override fun getImportNames(diagnostics: Collection<Diagnostic>, element: JetExpression): Set<String> {
        return diagnostics.mapTo(LinkedHashSet()) { if (it.toString().contains("setValue")) "setValue" else "getValue" }
    }

    override fun getCallTypeAndReceiver() = CallTypeAndReceiver.DELEGATE(element as JetExpression)

    override fun getSupportedErrors() = ERRORS

    companion object : JetSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): JetIntentionAction<JetExpression>? {
            assert(diagnostic.factory == Errors.DELEGATE_SPECIAL_FUNCTION_MISSING)
            return MissingDelegateAccessorsAutoImportFix(diagnostic.psiElement as JetExpression, listOf(diagnostic))
        }

        override fun canFixSeveralSameProblems(): Boolean = true
        override fun doCreateActions(sameTypeDiagnostics: List<Diagnostic>): List<IntentionAction> {
            val first = sameTypeDiagnostics.first()
            val element = first.psiElement

            if (element !is JetExpression || sameTypeDiagnostics.any { it.factory != Errors.DELEGATE_SPECIAL_FUNCTION_MISSING }) {
                return emptyList()
            }

            return listOf(MissingDelegateAccessorsAutoImportFix(element, sameTypeDiagnostics))
        }

        private val ERRORS by lazy(LazyThreadSafetyMode.PUBLICATION) { QuickFixes.getInstance().getDiagnostics(this) }
    }
}

class MissingComponentsAutoImportFix(element: JetExpression, diagnostics: Collection<Diagnostic>) : AutoImportFixBase(element, diagnostics) {
    override fun createAction(project: Project, editor: Editor): KotlinAddImportAction {
        return KotlinAddImportAction(project, editor, element, suggestions)
    }

    override fun getImportNames(diagnostics: Collection<Diagnostic>, element: JetExpression): List<String> {
        return diagnostics.map { Errors.COMPONENT_FUNCTION_MISSING.cast(it).a.identifier }
    }

    override fun getCallTypeAndReceiver() = CallTypeAndReceiver.OPERATOR(element as JetExpression)

    override fun getSupportedErrors() = ERRORS

    companion object : JetSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): JetIntentionAction<JetExpression>? {
            assert(diagnostic.factory == Errors.COMPONENT_FUNCTION_MISSING)
            return MissingComponentsAutoImportFix(diagnostic.psiElement as JetExpression, listOf(diagnostic))
        }

        override fun canFixSeveralSameProblems(): Boolean = true
        override fun doCreateActions(sameTypeDiagnostics: List<Diagnostic>): List<IntentionAction> {
            val first = sameTypeDiagnostics.first()
            val element = first.psiElement

            if (element !is JetExpression || sameTypeDiagnostics.any { it.factory != Errors.COMPONENT_FUNCTION_MISSING }) {
                return emptyList()
            }

            return listOf(MissingComponentsAutoImportFix(element, sameTypeDiagnostics))
        }

        private val ERRORS by lazy(LazyThreadSafetyMode.PUBLICATION) { QuickFixes.getInstance().getDiagnostics(this) }
    }
}