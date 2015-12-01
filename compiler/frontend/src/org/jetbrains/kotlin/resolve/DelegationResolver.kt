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

package org.jetbrains.kotlin.resolve

import com.google.common.base.Predicate
import com.google.common.collect.Collections2
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor.Kind.DELEGATION
import org.jetbrains.kotlin.diagnostics.Errors.MANY_IMPL_MEMBER_NOT_IMPLEMENTED
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDelegatorByExpressionSpecifier
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.OverridingUtil.OverrideCompatibilityInfo.Result.OVERRIDABLE
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import java.util.*

class DelegationResolver<T : CallableMemberDescriptor> private constructor(
        private val classOrObject: KtClassOrObject,
        private val ownerDescriptor: ClassDescriptor,
        private val existingMembers: Collection<CallableDescriptor>,
        private val trace: BindingTrace,
        private val memberExtractor: DelegationResolver.MemberExtractor<T>,
        private val typeResolver: DelegationResolver.TypeResolver) {

    private fun generateDelegatedMembers(): Collection<T> {
        val delegatedMembers = HashSet<T>()
        for (delegationSpecifier in classOrObject.getDelegationSpecifiers()) {
            if (delegationSpecifier !is KtDelegatorByExpressionSpecifier) {
                continue
            }
            val typeReference = delegationSpecifier.typeReference ?: continue
            val delegatedTraitType = typeResolver.resolve(typeReference)
            if (delegatedTraitType == null || delegatedTraitType.isError) {
                continue
            }
            val delegatesForTrait = generateDelegatesForTrait(delegatedMembers, delegatedTraitType)
            delegatedMembers.addAll(delegatesForTrait)
        }
        return delegatedMembers
    }

    private fun generateDelegatesForTrait(
            existingDelegates: Collection<T>,
            delegatedTraitType: KotlinType): Collection<T> {
        val result = HashSet<T>()
        val candidates = generateDelegationCandidates(delegatedTraitType)
        for (candidate in candidates) {
            if (existingMemberOverridesDelegatedMember(candidate, existingMembers)) {
                continue
            }
            //only leave the first delegated member
            if (checkClashWithOtherDelegatedMember(existingDelegates, candidate)) {
                continue
            }

            result.add(candidate)
        }
        return result
    }

    private fun generateDelegationCandidates(delegatedTraitType: KotlinType): Collection<T> {
        val descriptorsToDelegate = overridableMembersNotFromSuperClassOfTrait(delegatedTraitType)
        val result = ArrayList<T>(descriptorsToDelegate.size)
        for (memberDescriptor in descriptorsToDelegate) {
            val newModality = if (memberDescriptor.modality == Modality.ABSTRACT) Modality.OPEN else memberDescriptor.modality
            @Suppress("UNCHECKED_CAST")
            val copy = memberDescriptor.copy(ownerDescriptor, newModality, Visibilities.INHERITED, DELEGATION, false) as T
            result.add(copy)
        }
        return result
    }

    private fun checkClashWithOtherDelegatedMember(delegatedMembers: Collection<T>, candidate: T): Boolean {
        for (alreadyDelegatedMember in delegatedMembers) {
            if (haveSameSignatures(alreadyDelegatedMember, candidate)) {
                //trying to delegate to many traits with the same methods
                trace.report(MANY_IMPL_MEMBER_NOT_IMPLEMENTED.on(classOrObject, classOrObject, alreadyDelegatedMember))
                return true
            }
        }
        return false
    }

    private fun overridableMembersNotFromSuperClassOfTrait(trait: KotlinType): Collection<T> {
        val membersToSkip = getMembersFromClassSupertypeOfTrait(trait)
        return Collections2.filter(
                memberExtractor.getMembersByType(trait),
                Predicate<org.jetbrains.kotlin.descriptors.CallableMemberDescriptor> { descriptor ->
                    if (!descriptor!!.modality.isOverridable) {
                        return@Predicate false
                    }
                    for (memberToSkip in membersToSkip) {
                        if (haveSameSignatures(memberToSkip, descriptor)) {
                            return@Predicate false
                        }
                    }
                    true
                })
    }

    private fun getMembersFromClassSupertypeOfTrait(traitType: KotlinType): Collection<T> {
        var classSupertype: KotlinType? = null
        for (supertype in TypeUtils.getAllSupertypes(traitType)) {
            if (isNotTrait(supertype.constructor.declarationDescriptor)) {
                classSupertype = supertype
                break
            }
        }
        return if (classSupertype != null) memberExtractor.getMembersByType(classSupertype) else emptyList<T>()
    }

    interface MemberExtractor<T : CallableMemberDescriptor> {
        fun getMembersByType(type: KotlinType): Collection<T>
    }

    interface TypeResolver {
        fun resolve(reference: KtTypeReference): KotlinType?
    }

    companion object {

        fun <T : CallableMemberDescriptor> generateDelegatedMembers(
                classOrObject: KtClassOrObject,
                ownerDescriptor: ClassDescriptor,
                existingMembers: Collection<CallableDescriptor>,
                trace: BindingTrace,
                memberExtractor: MemberExtractor<T>,
                typeResolver: TypeResolver): Collection<T> {
            return DelegationResolver(classOrObject, ownerDescriptor, existingMembers, trace, memberExtractor, typeResolver).generateDelegatedMembers()
        }

        private fun existingMemberOverridesDelegatedMember(
                candidate: CallableMemberDescriptor,
                existingMembers: Collection<CallableDescriptor>): Boolean {
            for (existingDescriptor in existingMembers) {
                if (haveSameSignatures(existingDescriptor, candidate)) {
                    return true
                }
            }
            return false
        }

        private fun haveSameSignatures(memberOne: CallableDescriptor, memberTwo: CallableDescriptor): Boolean {
            //isOverridableBy ignores return types
            return OverridingUtil.DEFAULT.isOverridableBy(memberOne, memberTwo).result == OVERRIDABLE
        }

        private fun isNotTrait(descriptor: DeclarationDescriptor?): Boolean {
            if (descriptor is ClassDescriptor) {
                val kind = descriptor.kind
                return kind != ClassKind.INTERFACE
            }
            return false
        }
    }
}
