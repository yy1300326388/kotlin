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
import org.jetbrains.kotlin.cfg.pseudocodeTraverser.TraversalOrder
import org.jetbrains.kotlin.cfg.pseudocodeTraverser.traverse
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.JetCallExpression
import org.jetbrains.kotlin.psi.JetConstructor
import org.jetbrains.kotlin.psi.JetExpression
import org.jetbrains.kotlin.psi.JetThisExpression
import org.jetbrains.kotlin.psi.psiUtil.getAnnotationEntries
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.types.TypeUtils

public class JetConstructorConsistencyChecker private constructor(private val constructor: JetConstructor<*>, private val trace: BindingTrace) {

    private val pseudocode = JetControlFlowProcessor(trace).generatePseudocode(constructor)

    private val variablesData = PseudocodeVariablesData(pseudocode, trace.bindingContext)

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

    public fun check() {
        val propertyDescriptors = variablesData.getDeclaredVariables(pseudocode, false).filterIsInstance<PropertyDescriptor>()
        pseudocode.traverse(
                TraversalOrder.FORWARD, variablesData.variableInitializers, { instruction, enterData, exitData ->

            fun notNullPropertiesInitialized(): Boolean {
                for (descriptor in propertyDescriptors) {
                    if (enterData[descriptor]?.isInitialized != true) {
                        return false
                    }
                }
                return true
            }

            when (instruction) {
                is ReadValueInstruction ->
                        if (instruction.element is JetThisExpression) {
                            if (!notNullPropertiesInitialized() && !markedAsFragile(instruction.element)) {
                                trace.report(Errors.DANGEROUS_THIS_IN_CONSTRUCTOR.on(instruction.element))

                            }
                        }
                is MagicInstruction ->
                        if (instruction.kind == MagicKind.IMPLICIT_RECEIVER && instruction.element is JetCallExpression) {
                            if (!notNullPropertiesInitialized() && !markedAsFragile(instruction.element)) {
                                trace.report(Errors.DANGEROUS_METHOD_CALL_IN_CONSTRUCTOR.on(instruction.element))
                            }
                        }
            }
        })
    }

    companion object {
        public fun check(constructor: JetConstructor<*>, trace: BindingTrace) {
            JetConstructorConsistencyChecker(constructor, trace).check()
        }
    }
}