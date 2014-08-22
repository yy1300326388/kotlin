/*
 * Copyright 2010-2014 JetBrains s.r.o.
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

package org.jetbrains.jet.lang.resolve.lazy

import org.jetbrains.jet.lang.resolve.BindingContext
import org.jetbrains.jet.lang.resolve.Diagnostics
import org.jetbrains.jet.util.slicedmap.ReadOnlySlice
import org.jetbrains.jet.util.slicedmap.WritableSlice
import com.google.common.collect.ImmutableMap
import org.jetbrains.jet.lang.psi.JetDeclaration
import kotlin.properties.Delegates

public class LazyBindingContext: BindingContext {
    public var resolveSession: ResolveSession by Delegates.notNull()

    override fun getDiagnostics(): Diagnostics {
        throw UnsupportedOperationException()
    }

    override fun <K, V> get(slice: ReadOnlySlice<K, V>?, key: K?): V? {
        if (key == null) return null;

        // Trigger lazy resolve for declarations
        when (slice) {
            BindingContext.FUNCTION -> {
                resolveSession.resolveToDescriptor(key as JetDeclaration)
            }
        }

        return resolveSession.getBindingContext()[slice, key];
    }

    override fun <K, V> getKeys(slice: WritableSlice<K, V>?): Collection<K> {
        throw UnsupportedOperationException()
    }

    override fun <K, V> getSliceContents(slice: ReadOnlySlice<K, V>): ImmutableMap<K, V> {
        throw UnsupportedOperationException()
    }
}
