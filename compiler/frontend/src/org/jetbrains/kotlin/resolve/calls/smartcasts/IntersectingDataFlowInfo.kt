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
import org.jetbrains.kotlin.types.CommonSupertypes
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeIntersector
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
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

    override fun clearValueInfo(value: DataFlowValue): DataFlowInfo {
        val newTypeInfo = Maps.newHashMap(typeInfo)
        newTypeInfo.remove(value)
        return IntersectingDataFlowInfo(newTypeInfo)
    }

    override fun assign(a: DataFlowValue, b: DataFlowValue): DataFlowInfo {
        throw UnsupportedOperationException()
    }

    override fun equate(a: DataFlowValue, b: DataFlowValue): DataFlowInfo {
        throw UnsupportedOperationException()
    }

    override fun disequate(a: DataFlowValue, b: DataFlowValue): DataFlowInfo {
        throw UnsupportedOperationException()
    }

    override fun establishSubtyping(value: DataFlowValue, type: KotlinType): DataFlowInfo {
        val newTypeInfo = Maps.newHashMap(typeInfo)
        val previous = typeInfo[value] ?: value.type
        newTypeInfo[value] = TypeIntersector.intersectTypes(KotlinTypeChecker.DEFAULT, listOf(previous, type))
        return IntersectingDataFlowInfo(newTypeInfo)
    }

    override fun and(other: DataFlowInfo): DataFlowInfo {
        val newTypeInfo = Maps.newHashMap(typeInfo)
        other as IntersectingDataFlowInfo
        for ((key, type) in other.typeInfo) {
            if (newTypeInfo.contains(key)) {
                newTypeInfo[key] = TypeIntersector.intersectTypes(KotlinTypeChecker.DEFAULT, listOf(newTypeInfo[key], type))
            }
            else {
                newTypeInfo[key] = type
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
                newTypeInfo[key] = CommonSupertypes.commonSupertype(listOf(thisType, otherType))
            }
        }
        return IntersectingDataFlowInfo(newTypeInfo)
    }

}