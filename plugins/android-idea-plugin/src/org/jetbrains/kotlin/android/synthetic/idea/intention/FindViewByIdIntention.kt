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

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiElement
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.android.synthetic.AndroidConst
import org.jetbrains.kotlin.android.synthetic.idea.handleAndroidSyntheticScopes
import org.jetbrains.kotlin.android.synthetic.res.AndroidSyntheticProperty
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.*
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf

class FindViewByIdIntention : BaseAndroidExtensionsIntention<KtExpression>(
        KtExpression::class.java,
        "Simplify findViewById() with Android Extensions"
) {
    private interface PropertyCandidate

    private class PropertyCandidateWithWrongType(val property: AndroidSyntheticProperty) : PropertyCandidate {
        val typeString: String
            get() = (property as? PropertyDescriptor)?.type?.constructor?.declarationDescriptor?.fqNameUnsafe?.asString() ?: "<error>"
    }

    private class GoodPropertyCandidate(
            val property: AndroidSyntheticProperty,
            val widgetId: String,
            private val forView: Boolean
    ) : PropertyCandidate {
        val fqName: String
            get() {
                val variantName = property.variantName
                val layoutName = property.layoutName
                val forViewString = if (forView) "view." else ""
                return "${AndroidConst.SYNTHETIC_PACKAGE}.$variantName.$layoutName.$forViewString$widgetId"
            }
    }

    override fun isApplicableTo(element: KtExpression, caretOffset: Int): Boolean {
        fun PsiElement?.requireFindViewByIdCall() = requireCall(FIND_VIEW_BY_ID, 1) {
            val bindingContext = analyze()
            val resolvedCall = getResolvedCall(bindingContext) ?: return false

            valueArguments[0].getArgumentExpression().require<KtDotQualifiedExpression> {
                receiverExpression.require<KtDotQualifiedExpression> {
                    if (selectorExpression?.text != "id") return null

                    val androidFacet = AndroidFacet.getInstance(element) ?: return null
                    val mainPackage = androidFacet.manifest?.`package`?.xmlAttributeValue?.value

                    val rClass = receiverExpression.references.firstOrNull { it is KtSimpleNameReference }?.resolve()?.let {
                        bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, it] } ?: return null
                    if (rClass.fqNameUnsafe.asString() != mainPackage + ".R") return null

                    true
                }
                true
            }

            isValueParameterTypeOf(0, resolvedCall) { it.builtIns.intType == it.defaultType }
        }

        return element.require<KtBinaryExpressionWithTypeRHS>() {
            operation.require<KtSimpleNameExpression>("as")
            && (left.requireFindViewByIdCall() || left.require<KtDotQualifiedExpression> {
                selector.requireFindViewByIdCall()
            })
        }
    }

    override fun replaceWith(element: KtExpression, psiFactory: KtPsiFactory): NewElement? {
        element.require<KtBinaryExpressionWithTypeRHS>() {
            val typeRef = right

            val newElement = left.handleFindViewByIdCall(psiFactory, typeRef)
            if (newElement != null) return newElement

            left.require<KtDotQualifiedExpression> {
                return selector.handleFindViewByIdCall(psiFactory, typeRef, receiverExpression)
            }
        }
        return null
    }

    fun PsiElement?.handleFindViewByIdCall(
            psiFactory: KtPsiFactory,
            requiredWidgetTypeRef: KtTypeReference?,
            receiverExpressionForCall: KtExpression? = null
    ): NewElement? {
        fun createNewElement(candidate: GoodPropertyCandidate): NewElement {
            val receiverExpressionText = if (receiverExpressionForCall != null) receiverExpressionForCall.text + '.' else ""
            return NewElement(psiFactory.createExpression(receiverExpressionText + candidate.widgetId), candidate.fqName)
        }

        requireCall(FIND_VIEW_BY_ID, 1) {
            val bindingContext = (parent as KtElement).analyze(BodyResolveMode.PARTIAL)
            val resolvedCall = getResolvedCall(bindingContext) ?: return null
            val requiredWidgetType = requiredWidgetTypeRef?.let { bindingContext[BindingContext.TYPE, it] }

            valueArguments[0].getArgumentExpression().require<KtDotQualifiedExpression> {
                val widgetId = selectorExpression?.text ?: return null
                receiverExpression.require<KtDotQualifiedExpression> {
                    if (selectorExpression?.text != "id") return null

                    val resultingDescriptor = resolvedCall.resultingDescriptor
                    val dispatchReceiverParameterType = resultingDescriptor.dispatchReceiverParameter?.type ?: return null

                    val candidates = getCandidateDescriptors(
                            widgetId, dispatchReceiverParameterType, requiredWidgetType, findModuleDescriptor())

                    candidates.firstOrNull { it is GoodPropertyCandidate }?.let { return createNewElement(it as GoodPropertyCandidate) }
                    val firstCandidate = candidates.firstOrNull() ?: return null

                    if (firstCandidate is PropertyCandidateWithWrongType) {
                        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
                        HintManager.getInstance().showErrorHint(editor,
                                "Property $widgetId has an incompatible type: ${firstCandidate.typeString}")
                    }

                    true
                }
            }
        }

        return null
    }

    private fun getCandidateDescriptors(
            widgetId: String,
            receiverType: KotlinType,
            widgetType: KotlinType?,
            module: ModuleDescriptor
    ): List<PropertyCandidate> {
        val nameName = Name.identifier(widgetId)

        val candidates = arrayListOf<PropertyCandidate>()

        module.handleAndroidSyntheticScopes { scope ->
            for (property in scope.getContributedVariables(nameName, NoLookupLocation.FROM_IDE)) {
                val syntheticProperty = property as? AndroidSyntheticProperty ?: continue
                if (syntheticProperty.isErrorType) continue
                if (receiverType != property.extensionReceiverParameter?.type) continue

                if (widgetType != null && !property.type.isSubtypeOf(widgetType)) {
                    candidates += PropertyCandidateWithWrongType(syntheticProperty)
                }
                else {
                    val forView = receiverType.constructor.declarationDescriptor?.fqNameUnsafe?.asString() == AndroidConst.VIEW_FQNAME
                    candidates += GoodPropertyCandidate(syntheticProperty, widgetId, forView)
                }
            }
        }

        return candidates
    }

    private companion object {
        val FIND_VIEW_BY_ID = "findViewById"
    }

}
