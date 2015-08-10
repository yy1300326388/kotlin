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
import org.jetbrains.kotlin.psi.JetThisExpression
import org.jetbrains.kotlin.resolve.BindingTrace

public class JetConstructorConsistencyChecker private constructor(private val constructor: JetConstructor<*>, private val trace: BindingTrace) {

    private val pseudocode = JetControlFlowProcessor(trace).generatePseudocode(constructor)

    private val variablesData = PseudocodeVariablesData(pseudocode, trace.bindingContext)

    public fun check() {
        val classOrObject = constructor.getContainingClassOrObject()
        val properties = classOrObject.getBody()?.properties.orEmpty()
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
                            if (!notNullPropertiesInitialized()) {
                                trace.report(Errors.DANGEROUS_THIS_IN_CONSTRUCTOR.on(instruction.element))

                            }
                        }
                is MagicInstruction ->
                        if (instruction.kind == MagicKind.IMPLICIT_RECEIVER && instruction.element is JetCallExpression) {
                            if (!notNullPropertiesInitialized()) {
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