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

package org.jetbrains.kotlin.resolve.calls.results

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.calls.model.DefaultValueArgument
import org.jetbrains.kotlin.resolve.calls.model.MutableResolvedCall
import org.jetbrains.kotlin.types.BoundsSubstitutor
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.SmartSet


class ConflictingCandidateCall<D : CallableDescriptor> private constructor(
        val resolvedCall: MutableResolvedCall<D>,
        private val argumentsToParameters: Map<KtExpression, ValueParameterDescriptor>,
        val numberOfVarargArguments: Int,
        val numberOfDefaultArguments: Int
) {
    override fun toString(): String =
            "$resolvedCall :: $numberOfVarargArguments, $numberOfDefaultArguments"

    val resultingDescriptor: D
        get() = resolvedCall.resultingDescriptor

    val numberOfExplicitArguments: Int
        get() = argumentsToParameters.size

    val argumentExpressions: Collection<KtExpression>
        get() = argumentsToParameters.keys

    val callElement: KtElement
        get() = resolvedCall.call.callElement

    val isGeneric: Boolean
        get() = resolvedCall.resultingDescriptor.original.typeParameters.isNotEmpty()

    private val upperBoundsSubstitutor =
            BoundsSubstitutor.createUpperBoundsSubstitutor(resolvedCall.resultingDescriptor)

    fun getExtensionReceiverType(substituteUpperBounds: Boolean): KotlinType? =
            resultingDescriptor.extensionReceiverParameter?.type?.let {
                extensionReceiverType ->
                if (substituteUpperBounds)
                    upperBoundsSubstitutor.substitute(extensionReceiverType, Variance.INVARIANT)
                else
                    extensionReceiverType
            }

    fun getParameterType(argumentExpression: KtExpression, substituteUpperBounds: Boolean): KotlinType? =
            argumentsToParameters[argumentExpression]?.let {
                valueParameterDescriptor ->
                val parameterType = valueParameterDescriptor.varargElementType ?: valueParameterDescriptor.type
                if (substituteUpperBounds)
                    upperBoundsSubstitutor.substitute(parameterType, Variance.INVARIANT)
                else
                    parameterType
            }

    companion object {
        fun <D : CallableDescriptor> create(
                call: MutableResolvedCall<D>
        ): ConflictingCandidateCall<D> {
            val argumentsToParameters = hashMapOf<KtExpression, ValueParameterDescriptor>()
            val varargArguments = SmartSet.create<ValueParameterDescriptor>()
            val defaultValueArguments = SmartSet.create<ValueParameterDescriptor>()

            for ((valueParameterDescriptor, resolvedValueArgument) in call.valueArguments.entries) {
                if (valueParameterDescriptor.varargElementType != null) {
                    varargArguments.add(valueParameterDescriptor)
                }
                if (resolvedValueArgument is DefaultValueArgument) {
                    defaultValueArguments.add(valueParameterDescriptor)
                }
                for (valueArgument in resolvedValueArgument.arguments) {
                    valueArgument.getArgumentExpression()?.let {
                        argumentsToParameters[it] = valueParameterDescriptor
                    }
                }
            }

            return ConflictingCandidateCall(
                    call,
                    argumentsToParameters,
                    numberOfDefaultArguments = defaultValueArguments.size,
                    numberOfVarargArguments = varargArguments.size
            )
        }
    }
}

