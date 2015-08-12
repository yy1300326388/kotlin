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

package org.jetbrains.kotlin.cfg

import org.jetbrains.kotlin.cfg.pseudocode.instructions.eval.MagicInstruction
import org.jetbrains.kotlin.cfg.pseudocode.instructions.eval.MagicKind
import org.jetbrains.kotlin.cfg.pseudocode.instructions.eval.ReadValueInstruction
import org.jetbrains.kotlin.cfg.pseudocode.instructions.Instruction
import org.jetbrains.kotlin.cfg.pseudocodeTraverser.TraversalOrder
import org.jetbrains.kotlin.cfg.pseudocodeTraverser.traverse
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getAnnotationEntries
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.expressions.OperatorConventions

public class JetConstructorConsistencyChecker private constructor(private val declaration: JetDeclaration, private val trace: BindingTrace) {

    private val classOrObject = declaration as? JetClassOrObject ?: (declaration as JetConstructor<*>).getContainingClassOrObject()

    private val classDescriptor = trace.get(BindingContext.CLASS, classOrObject)

    private val overridableClass = classDescriptor?.modality?.isOverridable ?: false

    private val pseudocode = JetControlFlowProcessor(trace).generatePseudocode(declaration)

    private val variablesData = PseudocodeVariablesData(pseudocode, trace.bindingContext)

    private fun checkReferenceSafety(reference: JetReferenceExpression): Boolean {
        if (JetPsiUtil.isBackingFieldReference(reference)) return true
        val descriptor = trace.get(BindingContext.REFERENCE_TARGET, reference)
        if (descriptor is PropertyDescriptor) {
            if (overridableClass && descriptor.modality.isOverridable) {
                trace.report(Errors.DANGEROUS_OPEN_PROPERTY_ACCESS_IN_CONSTRUCTOR.on(reference))
                return true
            }
            if (descriptor.containingDeclaration != classDescriptor) return true
            return descriptor.getter?.isDefault != false && descriptor.setter?.isDefault != false
        }
        return true
    }

    private fun markedAsFragile(expression: JetExpression): Boolean {
        val annotationEntries = expression.getAnnotationEntries()
        for (entry in annotationEntries) {
            val descriptor = trace.get(BindingContext.ANNOTATION, entry) ?: continue
            if (descriptor.type.isError) continue
            val classDescriptor = TypeUtils.getClassDescriptor(descriptor.type) ?: continue
            if (classDescriptor == classDescriptor.builtIns.fragileAnnotation) return true
        }
        return false
    }

    private fun safeThisUsage(expression: JetThisExpression): Boolean {
        val reference = expression.instanceReference
        val referenceDescriptor = trace.get(BindingContext.REFERENCE_TARGET, reference)
        if (referenceDescriptor != classDescriptor) return true
        val parent = expression.parent
        when (parent) {
            is JetQualifiedExpression ->
                if (parent.selectorExpression is JetSimpleNameExpression) {
                    return checkReferenceSafety(parent.selectorExpression as JetSimpleNameExpression)
               }
            is JetBinaryExpression -> return OperatorConventions.EQUALS_OPERATIONS.contains(parent.operationToken) ||
                                             OperatorConventions.IDENTITY_EQUALS_OPERATIONS.contains(parent.operationToken)
        }
        return false
    }

    private fun safeCallUsage(expression: JetCallExpression): Boolean {
        val callee = expression.calleeExpression
        if (callee is JetReferenceExpression) {
            val descriptor = trace.get(BindingContext.REFERENCE_TARGET, callee)
            if (descriptor is FunctionDescriptor) {
                val containingDescriptor = descriptor.containingDeclaration
                if (containingDescriptor != classDescriptor) return true
            }
        }
        return false
    }

    public fun check() {
        // List of properties to initialize
        val propertyDescriptors = variablesData.getDeclaredVariables(pseudocode, false)
                .filterIsInstance<PropertyDescriptor>()
                .filter { trace.get(BindingContext.BACKING_FIELD_REQUIRED, it) == true }
        pseudocode.traverse(
                TraversalOrder.FORWARD, variablesData.variableInitializers, { instruction, enterData, exitData ->

            fun findNotNullUninitializedProperty(): PropertyDescriptor? {
                for (descriptor in propertyDescriptors) {
                    if (!descriptor.type.isMarkedNullable && enterData[descriptor]?.isInitialized != true) {
                        return descriptor
                    }
                }
                return null
            }

            fun reportDangerousMethodCall(expression: JetExpression) {
                val uninitialized = findNotNullUninitializedProperty()
                if (uninitialized != null) {
                    trace.report(Errors.DANGEROUS_METHOD_CALL_IN_CONSTRUCTOR.on(expression, uninitialized))
                }
                else if (overridableClass) {
                    trace.report(Errors.DANGEROUS_METHOD_CALL_IN_OPEN_CLASS_CONSTRUCTOR.on(expression))
                }

            }

            fun checkInstruction(instruction: Instruction) {
                if (instruction.owner != pseudocode) {
                    // We should miss *some* of this local declarations, but not all
                    return
                }
                when (instruction) {
                    is ReadValueInstruction ->
                        if (instruction.element is JetThisExpression) {
                            if (!safeThisUsage(instruction.element) && !markedAsFragile(instruction.element)) {
                                val uninitialized = findNotNullUninitializedProperty()
                                if (uninitialized != null) {
                                    trace.report(Errors.DANGEROUS_THIS_IN_CONSTRUCTOR.on(instruction.element, uninitialized))
                                }
                                else if (overridableClass) {
                                    trace.report(Errors.DANGEROUS_THIS_IN_OPEN_CLASS_CONSTRUCTOR.on(instruction.element))
                                }
                            }
                        }
                    is MagicInstruction ->
                        if (instruction.kind == MagicKind.IMPLICIT_RECEIVER) {
                            if (instruction.element is JetCallExpression) {
                                if (!safeCallUsage(instruction.element) && !markedAsFragile(instruction.element)) {
                                    reportDangerousMethodCall(instruction.element)
                                }
                            }
                            else if (instruction.element is JetReferenceExpression && !markedAsFragile(instruction.element)) {
                                if (!checkReferenceSafety(instruction.element)) {
                                    reportDangerousMethodCall(instruction.element)
                                }
                            }
                        }
                }
            }

            checkInstruction(instruction)

        })
    }

    companion object {
        public fun check(constructor: JetConstructor<*>, trace: BindingTrace) {
            JetConstructorConsistencyChecker(constructor, trace).check()
        }

        public fun check(classOrObject: JetClassOrObject, trace: BindingTrace) {
            JetConstructorConsistencyChecker(classOrObject, trace).check()
        }
    }
}