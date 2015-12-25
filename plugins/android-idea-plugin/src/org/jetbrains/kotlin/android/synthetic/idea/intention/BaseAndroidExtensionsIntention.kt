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

package org.jetbrains.kotlin.android.synthetic.idea.intention

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.findModuleDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.types.lowerIfFlexible

abstract class BaseAndroidExtensionsIntention<TElement : KtElement>(
        elementType: Class<TElement>,
        text: String,
        familyName: String = text
) : SelfTargetingIntention<TElement>(elementType, text, familyName) {

    protected fun KtCallExpression.isValueParameterTypeOf(
            parameterIndex: Int,
            resolvedCall: ResolvedCall<*>?,
            check: (ClassifierDescriptor) -> Boolean = { true }
    ): Boolean {
        val ctxArgumentDescriptor = (resolvedCall ?: getResolvedCall(analyze()))?.resultingDescriptor
                ?.valueParameters?.get(parameterIndex)?.type?.lowerIfFlexible()
                ?.constructor?.declarationDescriptor ?: return false
        return check(ctxArgumentDescriptor)
    }

    protected val KtDotQualifiedExpression.receiver: KtExpression?
        get() = receiverExpression

    protected val KtDotQualifiedExpression.selector: KtExpression?
        get() = selectorExpression

    protected val KtBinaryExpressionWithTypeRHS.operation: KtSimpleNameExpression
        get() = operationReference

    protected inline fun <reified E : PsiElement> PsiElement?.require(name: String? = null, sub: E.() -> Boolean): Boolean {
        return require<E>(name) && (this as E).sub()
    }

    protected inline fun <reified E : PsiElement> PsiElement?.require(name: String? = null): Boolean {
        if (this !is E) return false
        if (name != null && name != this.text) return false
        return true
    }

    protected inline fun PsiElement?.requireCall(
            functionName: String? = null,
            argCount: Int? = null,
            sub: KtCallExpression.() -> Boolean
    ) = requireCall(functionName, argCount) && (this as KtCallExpression).sub()

    @Suppress("NOTHING_TO_INLINE")
    protected inline fun PsiElement?.requireCall(functionName: String? = null, argCount: Int? = null): Boolean {
        if (this !is KtCallExpression) return false
        if (functionName != null && functionName != calleeExpression?.text) return false
        if (argCount != null && argCount != valueArguments.size) return false
        return true
    }

    abstract fun replaceWith(element: TElement, psiFactory: KtPsiFactory): NewElement?

    final override fun applyTo(element: TElement, editor: Editor) {
        val project = editor.project ?: return
        val file = element.containingFile as? KtFile ?: return
        val moduleDescriptor = file.findModuleDescriptor()
        val resolutionFacade = file.getResolutionFacade()

        val psiFactory = KtPsiFactory(project)
        val (newElement, fqNamesToImport) = replaceWith(element, psiFactory) ?: return

        val newExpression = newElement

        ImportInsertHelper.getInstance(project).apply {
            fqNamesToImport
                    .flatMap { resolutionFacade.resolveImportReference(moduleDescriptor, FqName(it)) }
                    .forEach { if (it.importableFqName != null) importDescriptor(file, it) }
        }

        element.replace(newExpression)
    }
}

class NewElement(val element: KtExpression, vararg val fqNamesToImport: String) {
    operator fun component1() = element
    operator fun component2() = fqNamesToImport
}