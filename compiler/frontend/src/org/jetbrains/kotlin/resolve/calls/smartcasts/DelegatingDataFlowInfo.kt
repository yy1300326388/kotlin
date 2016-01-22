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

package org.jetbrains.kotlin.resolve.calls.smartcasts

import com.google.common.collect.*
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.typeUtil.*

import java.util.*

import org.jetbrains.kotlin.resolve.calls.smartcasts.Nullability.NOT_NULL

internal class DelegatingDataFlowInfo private constructor(
        private val parent: DataFlowInfo?,
        private val nullabilityInfo: ImmutableMap<DataFlowValue, Nullability>,
        // Also immutable
        private val typeInfo: ImmutableSetMultimap<DataFlowValue, KotlinType>,
        /**
         * Value for which type info was cleared or reassigned at this point
         * so parent type info should not be in use
         */
        private val valueWithGivenTypeInfo: DataFlowValue?
) : DataFlowInfo {

    constructor(): this(null, ImmutableMap.of(), EMPTY_TYPE_INFO, null)

    override val completeNullabilityInfo: Map<DataFlowValue, Nullability>
        get() {
            val result = Maps.newHashMap<DataFlowValue, Nullability>()
            var info: DelegatingDataFlowInfo? = this
            while (info != null) {
                for ((key, value) in info.nullabilityInfo) {
                    if (!result.containsKey(key)) {
                        result.put(key, value)
                    }
                }
                info = info.parent as DelegatingDataFlowInfo?
            }
            return result
        }

    override val completeTypeInfo: SetMultimap<DataFlowValue, KotlinType>
        get() {
            val result = newTypeInfo()
            val withGivenTypeInfo = HashSet<DataFlowValue>()
            var info: DelegatingDataFlowInfo? = this
            while (info != null) {
                for (key in info.typeInfo.keySet()) {
                    if (!withGivenTypeInfo.contains(key)) {
                        result.putAll(key, info.typeInfo.get(key))
                    }
                }
                info.valueWithGivenTypeInfo?.let { withGivenTypeInfo.add(it) }
                info = info.parent as DelegatingDataFlowInfo?
            }
            return result.build()
        }

    override fun getCollectedNullability(key: DataFlowValue) = getNullability(key, false)

    override fun getPredictableNullability(key: DataFlowValue) = getNullability(key, true)

    private fun getNullability(key: DataFlowValue, predictableOnly: Boolean) =
            if (predictableOnly && !key.isPredictable) {
                key.immanentNullability
            }
            else {
                nullabilityInfo[key] ?: if (parent != null) {
                    parent.getCollectedNullability(key)
                }
                else {
                    key.immanentNullability
                }
            }

    private fun putNullability(map: MutableMap<DataFlowValue, Nullability>, value: DataFlowValue, nullability: Nullability): Boolean {
        map.put(value, nullability)
        return nullability != getCollectedNullability(value)
    }

    override fun getCollectedTypes(key: DataFlowValue) = getCollectedTypes(key, true)

    private fun getCollectedTypes(key: DataFlowValue, enrichWithNotNull: Boolean): Set<KotlinType> {
        val types = collectTypesFromMeAndParents(key)
        if (!enrichWithNotNull || getCollectedNullability(key).canBeNull()) {
            return types
        }

        val enrichedTypes = Sets.newHashSetWithExpectedSize<KotlinType>(types.size + 1)
        val originalType = key.type
        if (originalType.isMarkedNullable) {
            enrichedTypes.add(TypeUtils.makeNotNullable(originalType))
        }
        for (type in types) {
            enrichedTypes.add(TypeUtils.makeNotNullable(type))
        }

        return enrichedTypes
    }

    override fun getPredictableTypes(key: DataFlowValue) = getPredictableTypes(key, true)

    private fun getPredictableTypes(key: DataFlowValue, enrichWithNotNull: Boolean) =
            if (!key.isPredictable) LinkedHashSet() else getCollectedTypes(key, enrichWithNotNull)

    /**
     * Call this function to clear all data flow information about
     * the given data flow value.

     * @param value
     */
    override fun clearValueInfo(value: DataFlowValue): DataFlowInfo {
        val builder = Maps.newHashMap<DataFlowValue, Nullability>()
        putNullability(builder, value, Nullability.UNKNOWN)
        return create(this, ImmutableMap.copyOf(builder), EMPTY_TYPE_INFO, value)
    }

    override fun assign(a: DataFlowValue, b: DataFlowValue): DataFlowInfo {
        val nullability = Maps.newHashMap<DataFlowValue, Nullability>()
        val nullabilityOfB = getPredictableNullability(b)
        putNullability(nullability, a, nullabilityOfB)

        val newTypeInfo = newTypeInfo()
        var typesForB = getPredictableTypes(b)
        // Own type of B must be recorded separately, e.g. for a constant
        // But error types are not saved here
        if (!b.type.isError) {
            typesForB += b.type
        }
        newTypeInfo.putAll(a, typesForB)
        val builtTypeInfo = newTypeInfo.build()

        return create(this, ImmutableMap.copyOf(nullability), if (builtTypeInfo.isEmpty) EMPTY_TYPE_INFO else builtTypeInfo, a)
    }

    override fun equate(a: DataFlowValue, b: DataFlowValue): DataFlowInfo {
        val builder = Maps.newHashMap<DataFlowValue, Nullability>()
        val nullabilityOfA = getPredictableNullability(a)
        val nullabilityOfB = getPredictableNullability(b)

        var changed = putNullability(builder, a, nullabilityOfA.refine(nullabilityOfB)) or
                      putNullability(builder, b, nullabilityOfB.refine(nullabilityOfA))

        val newTypeInfo = newTypeInfo()
        newTypeInfo.putAll(a, getPredictableTypes(b, false))
        newTypeInfo.putAll(b, getPredictableTypes(a, false))
        if (a.type != b.type) {
            newTypeInfo.put(a, b.type)
            newTypeInfo.put(b, a.type)
        }
        val builtTypeInfo = newTypeInfo.build()
        changed = changed or !builtTypeInfo.isEmpty

        return if (!changed) {
            this
        }
        else {
            create(this, ImmutableMap.copyOf(builder), if (builtTypeInfo.isEmpty) EMPTY_TYPE_INFO else builtTypeInfo)
        }
    }

    private fun collectTypesFromMeAndParents(value: DataFlowValue): Set<KotlinType> {
        val types = LinkedHashSet<KotlinType>()

        var current: DataFlowInfo? = this
        while (current != null) {
            if (current is DelegatingDataFlowInfo) {
                types.addAll(current.typeInfo.get(value))
                if (value == current.valueWithGivenTypeInfo) {
                    current = null
                }
                else {
                    current = current.parent
                }
            }
            else {
                types.addAll(current.getCollectedTypes(value))
                break
            }
        }

        return types
    }

    override fun disequate(a: DataFlowValue, b: DataFlowValue): DataFlowInfo {
        val builder = Maps.newHashMap<DataFlowValue, Nullability>()
        val nullabilityOfA = getPredictableNullability(a)
        val nullabilityOfB = getPredictableNullability(b)

        var changed = putNullability(builder, a, nullabilityOfA.refine(nullabilityOfB.invert())) or
                      putNullability(builder, b, nullabilityOfB.refine(nullabilityOfA.invert()))
        return if (changed) create(this, ImmutableMap.copyOf(builder), EMPTY_TYPE_INFO) else this
    }

    override fun establishSubtyping(value: DataFlowValue, type: KotlinType): DataFlowInfo {
        if (value.type == type) return this
        if (getCollectedTypes(value).contains(type)) return this
        if (!value.type.isFlexible() && value.type.isSubtypeOf(type)) return this
        val newNullabilityInfo = if (type.isMarkedNullable) EMPTY_NULLABILITY_INFO else ImmutableMap.of(value, NOT_NULL)
        return create(this, newNullabilityInfo, ImmutableSetMultimap.of(value, type))
    }

    override fun and(other: DataFlowInfo): DataFlowInfo {
        if (other === DataFlowInfo.EMPTY) return this
        if (this === DataFlowInfo.EMPTY) return other
        if (this === other) return this

        assert(other is DelegatingDataFlowInfo) { "Unknown DataFlowInfo type: " + other }

        val nullabilityMapBuilder = Maps.newHashMap<DataFlowValue, Nullability>()
        for ((key, otherFlags) in other.completeNullabilityInfo) {
            val thisFlags = getCollectedNullability(key)
            val flags = thisFlags.and(otherFlags)
            if (flags != thisFlags) {
                nullabilityMapBuilder.put(key, flags)
            }
        }

        val myTypeInfo = completeTypeInfo
        val otherTypeInfo = other.completeTypeInfo
        if (nullabilityMapBuilder.isEmpty() && containsAll(myTypeInfo, otherTypeInfo)) {
            return this
        }

        return create(this, ImmutableMap.copyOf(nullabilityMapBuilder), ImmutableSetMultimap.copyOf(otherTypeInfo))
    }

    private fun Set<KotlinType>.containsNothing() = any { KotlinBuiltIns.isNothing(it) }

    private fun Set<KotlinType>.intersect(other: Set<KotlinType>) =
            if (other.containsNothing()) this
            else if (this.containsNothing()) other
            else Sets.intersection(this, other)

    override fun or(other: DataFlowInfo): DataFlowInfo {
        if (other === DataFlowInfo.EMPTY) return DataFlowInfo.EMPTY
        if (this === DataFlowInfo.EMPTY) return DataFlowInfo.EMPTY
        if (this === other) return this

        assert(other is DelegatingDataFlowInfo) { "Unknown DataFlowInfo type: " + other }

        val nullabilityMapBuilder = Maps.newHashMap<DataFlowValue, Nullability>()
        for ((key, otherFlags) in other.completeNullabilityInfo) {
            val thisFlags = getCollectedNullability(key)
            nullabilityMapBuilder.put(key, thisFlags.or(otherFlags))
        }

        val myTypeInfo = completeTypeInfo
        val otherTypeInfo = other.completeTypeInfo
        val newTypeInfo = newTypeInfo()

        for (key in Sets.intersection(myTypeInfo.keySet(), otherTypeInfo.keySet())) {
            newTypeInfo.putAll(key, myTypeInfo[key].intersect(otherTypeInfo[key]))
        }

        return create(null, ImmutableMap.copyOf(nullabilityMapBuilder), newTypeInfo.build())
    }

    override fun toString() = if (typeInfo.isEmpty && nullabilityInfo.isEmpty()) "EMPTY" else "Non-trivial DataFlowInfo"

    companion object {
        private val EMPTY_NULLABILITY_INFO = ImmutableMap.of<DataFlowValue, Nullability>()
        private val EMPTY_TYPE_INFO = ImmutableSetMultimap.of<DataFlowValue, KotlinType>()

        private fun containsAll(first: SetMultimap<DataFlowValue, KotlinType>, second: SetMultimap<DataFlowValue, KotlinType>) =
                first.entries().containsAll(second.entries())

        fun newTypeInfo() = ImmutableSetMultimap.builder<DataFlowValue, KotlinType>()

        private fun create(parent: DataFlowInfo?,
                           nullabilityInfo: ImmutableMap<DataFlowValue, Nullability>,
                           // NB: typeInfo must be mutable here!
                           typeInfo: ImmutableSetMultimap<DataFlowValue, KotlinType>,
                           valueWithGivenTypeInfo: DataFlowValue? = null
        ): DataFlowInfo {
            val newTypeInfo = newTypeInfo()
            for (value in typeInfo.keys()) {
                for (type in typeInfo[value]) {
                    // Remove original type and for not flexible type also all its supertypes (see also KT-10666)
                    if (if (value.type.isFlexible()) value.type != type else !value.type.isSubtypeOf(type)) {
                        newTypeInfo.put(value, type)
                    }
                }
            }
            val builtTypeInfo = newTypeInfo.build()
            if (nullabilityInfo.isEmpty() && builtTypeInfo.isEmpty && valueWithGivenTypeInfo == null) {
                return parent ?: DataFlowInfoFactory.EMPTY
            }
            return DelegatingDataFlowInfo(parent, nullabilityInfo, builtTypeInfo, valueWithGivenTypeInfo)
        }
    }
}
