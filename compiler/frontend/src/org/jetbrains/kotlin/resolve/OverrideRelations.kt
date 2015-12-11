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

import com.intellij.util.Function
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall


fun <D> isEquivalent(d1: D, d2: D, transform: Function<in D, out CallableDescriptor>): Boolean =
        andInvariant(d1, d2, transform) { f1, f2 -> DescriptorEquivalenceForOverrides.areEquivalent(f1.original, f2.original) }

fun <D> equals(d1: D, d2: D, transform: Function<in D, out CallableDescriptor>): Boolean =
        andInvariant(d1, d2, transform) { f1, f2 -> f1 === f2 }

fun <D> originalEquals(d1: D, d2: D, transform: Function<in D, out CallableDescriptor>): Boolean =
        andInvariant(d1, d2, transform) { f1, f2 -> f1.original === f2.original }

fun <D> overrides(d1: D, d2: D, transform: Function<in D, out CallableDescriptor>): Boolean =
        andInvariant(d1, d2, transform) { f1, f2 -> OverrideResolver.overrides(f1, f2) }

fun <D> isOverridableBy(d1: D, d2: D, transform: Function<in D, out CallableDescriptor>): Boolean =
        andInvariant(d1, d2, transform) {
            f1, f2 ->
            OverridingUtil.DEFAULT.isOverridableBy(f1, f2, null).result ==
                    OverridingUtil.OverrideCompatibilityInfo.Result.OVERRIDABLE
        }

private inline fun <D> andInvariant(
        d1: D,
        d2: D,
        transform: Function<in D, out CallableDescriptor>,
        basicRelation: (CallableDescriptor, CallableDescriptor) -> Boolean
): Boolean =
        if (d1 is VariableAsFunctionResolvedCall) {
            if (d2 !is VariableAsFunctionResolvedCall) {
                throw AssertionError("Variable-as-function call $d1 is compared with regular call $d2")
            }
            @Suppress("UNCHECKED_CAST")
            (basicRelation(transform.`fun`(d1.variableCall as D), transform.`fun`(d2.variableCall as D)) &&
             basicRelation(transform.`fun`(d1.functionCall as D), transform.`fun`(d2.functionCall as D)))
        }
        else {
            basicRelation(transform.`fun`(d1), transform.`fun`(d2))
        }