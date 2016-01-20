/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.resolve.calls.smartcasts

import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Maps
import com.google.common.collect.SetMultimap
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf
import org.jetbrains.kotlin.utils.addToStdlib.singletonOrEmptySet

// We have to store nullability separately due to incorrect nullability handling for unbounded generic types
internal data class TypeWithNullability(
        val type: KotlinType,
        val nullability: Nullability  = Nullability.fromFlags(TypeUtils.isNullableType(type),
                                                              !KotlinBuiltIns.isNothingOrNullableNothing(type))
) {
    fun makeNotNullable() = TypeWithNullability(TypeUtils.makeNotNullable(type), Nullability.fromFlags(false, nullability.canBeNonNull()))

    fun makeNullable() = TypeWithNullability(TypeUtils.makeNullable(type), Nullability.fromFlags(true, nullability.canBeNonNull()))
}

internal class IntersectingDataFlowInfo(private val typeInfo: Map<DataFlowValue, TypeWithNullability> = emptyMap()) : DataFlowInfo {

    override val completeNullabilityInfo: Map<DataFlowValue, Nullability> by lazy {
        typeInfo.mapValues {
            it.value.nullability
        }
    }

    override val completeTypeInfo: SetMultimap<DataFlowValue, KotlinType> by lazy {
        val multiMap = LinkedHashMultimap.create<DataFlowValue, KotlinType>()
        for ((value, type) in typeInfo) {
            multiMap.put(value, type.type)
        }
        multiMap
    }

    override fun getCollectedNullability(key: DataFlowValue): Nullability =
            typeInfo[key]?.nullability ?: key.immanentNullability

    override fun getPredictableNullability(key: DataFlowValue): Nullability =
            if (key.isPredictable) getCollectedNullability(key)
            else key.immanentNullability

    override fun getCollectedTypes(key: DataFlowValue): Set<KotlinType> =
            typeInfo[key]?.type.singletonOrEmptySet()

    override fun getPredictableTypes(key: DataFlowValue): Set<KotlinType> =
            if (key.isPredictable) getCollectedTypes(key)
            else emptySet()

    private fun DataFlowValue.immanentTypeWithNullability() = TypeWithNullability(type, immanentNullability)

    private fun getPredictableType(key: DataFlowValue) =
            if (key.isPredictable) typeInfo[key] ?: key.immanentTypeWithNullability() else key.immanentTypeWithNullability()

    override fun clearValueInfo(value: DataFlowValue): DataFlowInfo {
        val newTypeInfo = Maps.newHashMap(typeInfo)
        if (newTypeInfo.remove(value) != null) {
            return IntersectingDataFlowInfo(newTypeInfo)
        }
        return this
    }

    private fun intersectTypes(vararg types: TypeWithNullability): TypeWithNullability? {
        val type = TypeIntersector.intersectTypes(KotlinTypeChecker.DEFAULT, types.asList().map { it.type }) ?: return null
        val nullability = types.map { it.nullability }.fold(Nullability.UNKNOWN, { left, right -> left.and(right) } )
        return TypeWithNullability(type, nullability)
    }

    private fun MutableMap<DataFlowValue, TypeWithNullability>.setIntersection(
            value: DataFlowValue,
            vararg types: TypeWithNullability
    ): Boolean {
        val intersection = if (types.size > 1) {
            intersectTypes(*types) ?: return false
        }
        else if (types.isEmpty()) {
            return this.remove(value) != null
        }
        else {
            types.first()
        }.let { if (!TypeUtils.isNullableType(it.type) && it.nullability.canBeNull()) it.makeNullable() else it }
        if (!value.type.isFlexible()) {
            if (value.type.isSubtypeOf(intersection.type) && value.immanentNullability.isSubtypeOf(intersection.nullability)) {
                return false
            }
        }
        else {
            if (value.immanentTypeWithNullability() == intersection) {
                return false
            }
        }
        return this.put(value, intersection) != intersection
    }

    override fun assign(a: DataFlowValue, b: DataFlowValue): DataFlowInfo {
        val type = getPredictableType(b)
        val newTypeInfo = Maps.newHashMap(typeInfo)
        if (newTypeInfo.setIntersection(a, type, a.immanentTypeWithNullability())) {
            return IntersectingDataFlowInfo(newTypeInfo)
        }
        return this
    }


    override fun equate(a: DataFlowValue, b: DataFlowValue): DataFlowInfo {
        val type = intersectTypes(getPredictableType(a), getPredictableType(b))
                   ?: return this
        val newTypeInfo = Maps.newHashMap(typeInfo)
        if (newTypeInfo.setIntersection(a, type, a.immanentTypeWithNullability()) ||
            newTypeInfo.setIntersection(b, type, b.immanentTypeWithNullability())) {
            return IntersectingDataFlowInfo(newTypeInfo)
        }
        return this
    }

    override fun disequate(a: DataFlowValue, b: DataFlowValue): DataFlowInfo {
        val typeOfA = getPredictableType(a)
        val typeOfB = getPredictableType(b)
        val nullabilityOfA = typeOfA.nullability
        val nullabilityOfB = typeOfB.nullability
        val newTypeInfo = Maps.newHashMap(typeInfo)
        var changed = false
        if (nullabilityOfA == Nullability.NULL) {
            changed = changed or newTypeInfo.setIntersection(b, typeOfB.makeNotNullable())
        }
        if (nullabilityOfB == Nullability.NULL) {
            changed = changed or newTypeInfo.setIntersection(a, typeOfA.makeNotNullable())
        }
        return if (changed) IntersectingDataFlowInfo(newTypeInfo) else this
    }

    override fun establishSubtyping(value: DataFlowValue, type: KotlinType): DataFlowInfo {
        val newTypeInfo = Maps.newHashMap(typeInfo)
        val previous = typeInfo[value] ?: value.immanentTypeWithNullability()
        if (newTypeInfo.setIntersection(value, previous, TypeWithNullability(type))) {
            return IntersectingDataFlowInfo(newTypeInfo)
        }
        return this
    }

    override fun and(other: DataFlowInfo): DataFlowInfo {
        val newTypeInfo = Maps.newHashMap(typeInfo)
        other as IntersectingDataFlowInfo
        var changed = false
        for ((key, type) in other.typeInfo) {
            val existingType = newTypeInfo[key]
            if (existingType != null) {
                changed = changed or newTypeInfo.setIntersection(key, existingType, type)
            }
            else {
                changed = changed or newTypeInfo.setIntersection(key, type)
            }
        }
        return IntersectingDataFlowInfo(newTypeInfo)
    }

    override fun or(other: DataFlowInfo): DataFlowInfo {
        val newTypeInfo = Maps.newHashMap<DataFlowValue, TypeWithNullability>()
        other as IntersectingDataFlowInfo
        for ((key, otherType) in other.typeInfo) {
            val thisType = typeInfo[key]
            if (thisType != null) {
                newTypeInfo.setIntersection(key,
                                            TypeWithNullability(CommonSupertypes.commonSupertype(listOf(thisType.type, otherType.type)),
                                                                thisType.nullability.or(otherType.nullability)))
            }
        }
        return IntersectingDataFlowInfo(newTypeInfo)
    }

}