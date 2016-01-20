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

internal class IntersectingDataFlowInfo(private val typeInfo: Map<DataFlowValue, KotlinType> = emptyMap()) : DataFlowInfo {

    private fun KotlinType.nullability() =
            Nullability.fromFlags(TypeUtils.isNullableType(this), !KotlinBuiltIns.isNothingOrNullableNothing(this))

    override val completeNullabilityInfo: Map<DataFlowValue, Nullability> by lazy {
        typeInfo.mapValues {
            it.value.nullability()
        }
    }

    override val completeTypeInfo: SetMultimap<DataFlowValue, KotlinType> by lazy {
        val multiMap = LinkedHashMultimap.create<DataFlowValue, KotlinType>()
        for ((value, type) in typeInfo) {
            multiMap.put(value, type)
        }
        multiMap
    }

    override fun getCollectedNullability(key: DataFlowValue): Nullability =
            typeInfo[key]?.nullability() ?: key.immanentNullability

    override fun getPredictableNullability(key: DataFlowValue): Nullability =
            if (key.isPredictable) getCollectedNullability(key)
            else key.immanentNullability

    override fun getCollectedTypes(key: DataFlowValue): Set<KotlinType> =
            typeInfo[key].singletonOrEmptySet()

    override fun getPredictableTypes(key: DataFlowValue): Set<KotlinType> =
            if (key.isPredictable) getCollectedTypes(key)
            else emptySet()

    private fun getPredictableType(key: DataFlowValue) = if (key.isPredictable) typeInfo[key] ?: key.type else key.type

    override fun clearValueInfo(value: DataFlowValue): DataFlowInfo {
        val newTypeInfo = Maps.newHashMap(typeInfo)
        if (newTypeInfo.remove(value) != null) {
            return IntersectingDataFlowInfo(newTypeInfo)
        }
        return this
    }

    private fun intersectTypes(vararg types: KotlinType): KotlinType? {
        val type = TypeIntersector.intersectTypes(KotlinTypeChecker.DEFAULT, types.asList()) ?: return null
        val nullable = types.all { TypeUtils.isNullableType(it) }
        return if (nullable && !TypeUtils.isNullableType(type)) TypeUtils.makeNullable(type) else type
    }

    private fun MutableMap<DataFlowValue, KotlinType>.setIntersection(value: DataFlowValue, vararg types: KotlinType): Boolean {
        val intersection = if (types.size > 1) {
            intersectTypes(*types) ?: return false
        }
        else if (types.isEmpty()) {
            return this.remove(value) != null
        }
        else {
            types.first()
        }
        if (!value.type.isFlexible() && value.type.isSubtypeOf(intersection)) {
            return false
        }
        return this.put(value, intersection) !== intersection
    }

    override fun assign(a: DataFlowValue, b: DataFlowValue): DataFlowInfo {
        val type = getPredictableType(b)
        val newTypeInfo = Maps.newHashMap(typeInfo)
        if (newTypeInfo.setIntersection(a, type, a.type)) {
            return IntersectingDataFlowInfo(newTypeInfo)
        }
        return this
    }


    override fun equate(a: DataFlowValue, b: DataFlowValue): DataFlowInfo {
        val type = intersectTypes(getPredictableType(a), getPredictableType(b))
                   ?: return this
        val newTypeInfo = Maps.newHashMap(typeInfo)
        if (newTypeInfo.setIntersection(a, type, a.type) || newTypeInfo.setIntersection(b, type, b.type)) {
            return IntersectingDataFlowInfo(newTypeInfo)
        }
        return this
    }

    override fun disequate(a: DataFlowValue, b: DataFlowValue): DataFlowInfo {
        val typeOfA = getPredictableType(a)
        val typeOfB = getPredictableType(b)
        val nullabilityOfA = typeOfA.nullability()
        val nullabilityOfB = typeOfB.nullability()
        val newTypeInfo = Maps.newHashMap(typeInfo)
        if (nullabilityOfA == Nullability.NULL) {
            newTypeInfo[b] = TypeUtils.makeNotNullable(typeOfB)
        }
        else if (nullabilityOfB == Nullability.NULL) {
            newTypeInfo[a] = TypeUtils.makeNotNullable(typeOfA)
        }
        else {
            return this
        }
        return IntersectingDataFlowInfo(newTypeInfo)
    }

    override fun establishSubtyping(value: DataFlowValue, type: KotlinType): DataFlowInfo {
        val newTypeInfo = Maps.newHashMap(typeInfo)
        val previous = typeInfo[value] ?: value.type
        if (newTypeInfo.setIntersection(value, previous, type)) {
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
        val newTypeInfo = Maps.newHashMap<DataFlowValue, KotlinType>()
        other as IntersectingDataFlowInfo
        for ((key, otherType) in other.typeInfo) {
            val thisType = typeInfo[key]
            if (thisType != null) {
                newTypeInfo.setIntersection(key, CommonSupertypes.commonSupertype(listOf(thisType, otherType)))
            }
        }
        return IntersectingDataFlowInfo(newTypeInfo)
    }

}