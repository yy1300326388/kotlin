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

import gnu.trove.THashSet
import gnu.trove.TObjectHashingStrategy
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ScriptDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.resolve.OverrideResolver
import org.jetbrains.kotlin.resolve.calls.context.CheckArgumentTypesMode
import org.jetbrains.kotlin.resolve.calls.model.MutableResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.utils.addToStdlib.check

class OverloadingConflictResolver(private val builtIns: KotlinBuiltIns) {

    fun <D : CallableDescriptor> findMaximallySpecific(
            candidates: Set<MutableResolvedCall<D>>,
            discriminateGenericDescriptors: Boolean,
            checkArgumentsMode: CheckArgumentTypesMode
    ): MutableResolvedCall<D>? =
            when (checkArgumentsMode) {
                CheckArgumentTypesMode.CHECK_CALLABLE_TYPE -> {
                    singleCandidateCallOrNull(candidates) {
                        isMaxSpecific(it, candidates) {
                            call1, call2 ->
                            isMoreSpecificCallableReference(call1.resultingDescriptor, call2.resultingDescriptor)
                        }
                    }
                }

                CheckArgumentTypesMode.CHECK_VALUE_ARGUMENTS -> {
                    if (candidates.size > 1)
                        findMaximallySpecificCall(candidates, discriminateGenericDescriptors)
                    else
                        candidates.firstOrNull()
                }
            }

    private fun <D : CallableDescriptor> findMaximallySpecificCall(
            candidates: Set<MutableResolvedCall<D>>,
            discriminateGenericDescriptors: Boolean
    ): MutableResolvedCall<D>? {
        val filteredCandidates =
                if (candidates.first() is VariableAsFunctionResolvedCall) {
                    candidates.filterCandidates {
                        isMaxSpecific(it, candidates) {
                            vfCall1, vfCall2 ->
                            val v1 = (vfCall1 as VariableAsFunctionResolvedCall).variableCall.resultingDescriptor
                            val v2 = (vfCall2 as VariableAsFunctionResolvedCall).variableCall.resultingDescriptor
                            isMoreSpecificVariable(v1, v2, discriminateGenericDescriptors)
                        }
                    }
                }
                else candidates

        val conflictingCandidates = filteredCandidates.map { ConflictingCandidateCall.create(it) }

        return singleWrappedCandidateOrNull(conflictingCandidates) {
            candidate ->
            candidate.check {
                isMaxSpecific(candidate, conflictingCandidates) {
                    call1, call2 ->
                    isMoreSpecificConflictingCall(call1, call2, discriminateGenericDescriptors)

                }
            }?.resolvedCall
        }
    }

    private inline fun <D : CallableDescriptor> singleCandidateCallOrNull(
            candidates: Collection<MutableResolvedCall<D>>,
            predicate: (MutableResolvedCall<D>) -> Boolean
    ): MutableResolvedCall<D>? =
            if (candidates.size <= 1)
                candidates.firstOrNull()
            else
                candidates.filterCandidates(predicate).singleOrNull()

    private inline fun <D : CallableDescriptor> Collection<MutableResolvedCall<D>>.filterCandidates(
            predicate: (MutableResolvedCall<D>) -> Boolean
    ): Set<MutableResolvedCall<D>> =
            filterTo(createCandidatesSet<D>(), predicate)

    private inline fun <C, D : CallableDescriptor> singleWrappedCandidateOrNull(
            candidates: Collection<C>,
            unwrapIfSatisfies: (C) -> MutableResolvedCall<D>?
    ): MutableResolvedCall<D>? =
            candidates.mapNotNullTo(createCandidatesSet<D>(), unwrapIfSatisfies).singleOrNull()

    private inline fun <C> isMaxSpecific(
            candidate: C,
            candidates: Collection<C>,
            moreSpecific: (C, C) -> Boolean
    ): Boolean =
            candidates.all { other -> candidate == other || !definitelyNotSameOrMoreSpecific(candidate, other, moreSpecific) }

    private inline fun <C> definitelyNotSameOrMoreSpecific(
            candidate: C,
            other: C,
            moreSpecific: (C, C) -> Boolean
    ): Boolean =
            !moreSpecific(candidate, other) || moreSpecific(other, candidate)

    private fun <D : CallableDescriptor> isMoreSpecificConflictingCall(
            call1: ConflictingCandidateCall<D>,
            call2: ConflictingCandidateCall<D>,
            discriminateGenericDescriptors: Boolean
    ): Boolean {
        val fromScripts = compareDescriptorsFromScripts(call1.resultingDescriptor, call2.resultingDescriptor)
        if (fromScripts.decided) return fromScripts.moreSpecific

        val substituteParameterTypes =
            if (discriminateGenericDescriptors) {
                val call1IsGeneric = call1.isGeneric
                val call2IsGeneric = call2.isGeneric
                if (!call1IsGeneric && call2IsGeneric) return true
                if (call1IsGeneric && !call2IsGeneric) return false
                call1IsGeneric && call2IsGeneric
            }
            else false

        val asOverrides = compareDescriptorsAsPossibleOverrides(call1.resultingDescriptor, call2.resultingDescriptor)
        if (asOverrides.decided) return asOverrides.moreSpecific

        val byExtensionReceiverType = compareDescriptorsByExtensionReceiverType(call1.getExtensionReceiverType(substituteParameterTypes),
                                                                                call2.getExtensionReceiverType(substituteParameterTypes))
        if (byExtensionReceiverType.decided) return byExtensionReceiverType.moreSpecific

        assert(call1.callElement == call2.callElement) {
            "$call1 and $call2 correspond to different source elements"
        }
        assert(call1.numberOfExplicitArguments == call2.numberOfExplicitArguments) {
            "$call1 and $call2 have different number of explicit arguments"
        }

        for (argumentExpression in call1.argumentExpressions) {
            val type1 = call1.getParameterType(argumentExpression, substituteParameterTypes) ?:
                        throw AssertionError("Argument expression missing in $call1:\n${argumentExpression.text}")
            val type2 = call2.getParameterType(argumentExpression, substituteParameterTypes) ?:
                        throw AssertionError("Argument expression missing in $call2:\n${argumentExpression.text}")

            if (!typeMoreSpecific(type1, type2)) {
                return false
            }
        }

        if (call1.numberOfVarargArguments > call2.numberOfVarargArguments) {
            return false
        }

        if (call1.numberOfDefaultArguments > call2.numberOfDefaultArguments) {
            return false
        }

        return true
    }

    private enum class DescriptorComparisonResult(val decided: Boolean, val moreSpecific: Boolean) {
        DEFINITELY_LESS_SPECIFIC(true, false),
        UNDECIDED(false, false),
        DEFINITELY_MORE_SPECIFIC(true, true)
    }

    private fun compareDescriptorsFromScripts(
            d1: CallableDescriptor,
            d2: CallableDescriptor
    ): DescriptorComparisonResult {
        val containingDeclaration1 = d1.containingDeclaration
        val containingDeclaration2 = d2.containingDeclaration

        return if (containingDeclaration1 is ScriptDescriptor && containingDeclaration2 is ScriptDescriptor) {
            when {
                containingDeclaration1.priority > containingDeclaration2.priority ->
                    DescriptorComparisonResult.DEFINITELY_MORE_SPECIFIC
                containingDeclaration1.priority < containingDeclaration2.priority ->
                    DescriptorComparisonResult.DEFINITELY_LESS_SPECIFIC
                else ->
                    DescriptorComparisonResult.UNDECIDED
            }
        }
        else DescriptorComparisonResult.UNDECIDED
    }

    private fun compareDescriptorsAsPossibleOverrides(
            d1: CallableDescriptor,
            d2: CallableDescriptor
    ): DescriptorComparisonResult =
            when {
                OverrideResolver.overrides(d1, d2) ->
                    DescriptorComparisonResult.DEFINITELY_MORE_SPECIFIC
                OverrideResolver.overrides(d2, d1) ->
                    DescriptorComparisonResult.DEFINITELY_LESS_SPECIFIC
                else ->
                    DescriptorComparisonResult.UNDECIDED
            }

    private fun compareDescriptorsByExtensionReceiverType(
            type1: KotlinType?,
            type2: KotlinType?
    ): DescriptorComparisonResult {
        if (type1 != null && type2 != null) {
            if (!typeMoreSpecific(type1, type2))
                return DescriptorComparisonResult.DEFINITELY_LESS_SPECIFIC
        }
        return DescriptorComparisonResult.UNDECIDED
    }

    private fun isMoreSpecificVariable(
            v1: VariableDescriptor,
            v2: VariableDescriptor,
            discriminateGenericDescriptors: Boolean
    ): Boolean {
        val fromScripts = compareDescriptorsFromScripts(v1, v2)
        if (fromScripts.decided) return fromScripts.moreSpecific

        val isGenericV1 = isGeneric(v1)
        val isGenericV2 = isGeneric(v2)
        if (discriminateGenericDescriptors) {
            if (!isGenericV1 && isGenericV2) return true
            if (isGenericV1 && !isGenericV2) return false

            if (isGenericV1 && isGenericV2) {
                return isMoreSpecificVariable(BoundsSubstitutor.substituteBounds(v1),
                                              BoundsSubstitutor.substituteBounds(v2),
                                              false)
            }
        }

        val asOverrides = compareDescriptorsAsPossibleOverrides(v1, v2)
        if (asOverrides.decided) return asOverrides.moreSpecific

        val byExtensionReceiverType = compareDescriptorsByExtensionReceiverType(v1.extensionReceiverType, v2.extensionReceiverType)
        if (byExtensionReceiverType.decided) return byExtensionReceiverType.moreSpecific

        return true
    }

    private fun isMoreSpecificCallableReference(f: CallableDescriptor, g: CallableDescriptor): Boolean {
        val fromScripts = compareDescriptorsFromScripts(f, g)
        if (fromScripts.decided) return fromScripts.moreSpecific

        val asOverrides = compareDescriptorsAsPossibleOverrides(f, g)
        if (asOverrides.decided) return asOverrides.moreSpecific

        val byExtensionReceiverType = compareDescriptorsByExtensionReceiverType(f.extensionReceiverType, g.extensionReceiverType)
        if (byExtensionReceiverType.decided) return byExtensionReceiverType.moreSpecific

        val fParams = f.valueParameters
        val gParams = g.valueParameters

        val fSize = fParams.size
        val gSize = gParams.size

        if (fSize != gSize) return false

        for (i in 0..fSize - 1) {
            val fParam = fParams[i]
            val gParam = gParams[i]

            val fParamIsVararg = fParam.varargElementType != null
            val gParamIsVararg = gParam.varargElementType != null

            if (fParamIsVararg != gParamIsVararg) {
                return false
            }

            val fParamType = getVarargElementTypeOrType(fParam)
            val gParamType = getVarargElementTypeOrType(gParam)

            if (!typeMoreSpecific(fParamType, gParamType)) {
                return false
            }
        }

        return true
    }

    private fun getVarargElementTypeOrType(parameterDescriptor: ValueParameterDescriptor): KotlinType =
            parameterDescriptor.varargElementType ?: parameterDescriptor.type

    private fun isGeneric(f: CallableDescriptor): Boolean =
            f.original.typeParameters.isNotEmpty()

    private fun typeMoreSpecific(specific: KotlinType, general: KotlinType): Boolean {
        val isSubtype = KotlinTypeChecker.DEFAULT.isSubtypeOf(specific, general) || numericTypeMoreSpecific(specific, general)

        if (!isSubtype) return false

        val sThanG = specific.getSpecificityRelationTo(general)
        val gThanS = general.getSpecificityRelationTo(specific)
        if (sThanG == Specificity.Relation.LESS_SPECIFIC &&
            gThanS != Specificity.Relation.LESS_SPECIFIC) {
            return false
        }

        return true
    }

    private fun numericTypeMoreSpecific(specific: KotlinType, general: KotlinType): Boolean {
        val _double = builtIns.doubleType
        val _float = builtIns.floatType
        val _long = builtIns.longType
        val _int = builtIns.intType
        val _byte = builtIns.byteType
        val _short = builtIns.shortType

        when {
            TypeUtils.equalTypes(specific, _double) && TypeUtils.equalTypes(general, _float) -> return true
            TypeUtils.equalTypes(specific, _int) -> {
                when {
                    TypeUtils.equalTypes(general, _long) -> return true
                    TypeUtils.equalTypes(general, _byte) -> return true
                    TypeUtils.equalTypes(general, _short) -> return true
                }
            }
            TypeUtils.equalTypes(specific, _short) && TypeUtils.equalTypes(general, _byte) -> return true
        }

        return false
    }

    private val CallableDescriptor.extensionReceiverType: KotlinType?
        get() = extensionReceiverParameter?.type

    companion object {
        // Different smartcasts may lead to the same candidate descriptor wrapped into different ResolvedCallImpl objects
        private fun <D : CallableDescriptor> createCandidatesSet() =
                THashSet(getCallHashingStrategy<D>())

        private object CallHashingStrategy : TObjectHashingStrategy<MutableResolvedCall<*>> {
            override fun equals(call1: MutableResolvedCall<*>?, call2: MutableResolvedCall<*>?): Boolean =
                    if (call1 != null && call2 != null)
                        call1.resultingDescriptor == call2.resultingDescriptor
                    else
                        call1 == call2

            override fun computeHashCode(call: MutableResolvedCall<*>?): Int =
                    call?.resultingDescriptor?.hashCode() ?: 0
        }

        @Suppress("CAST_NEVER_SUCCEEDS")
        private fun <D : CallableDescriptor> getCallHashingStrategy() =
                CallHashingStrategy as TObjectHashingStrategy<MutableResolvedCall<D>>
    }
    
}
