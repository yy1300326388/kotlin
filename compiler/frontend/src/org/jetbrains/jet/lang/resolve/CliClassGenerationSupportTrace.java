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

package org.jetbrains.jet.lang.resolve;

import org.jetbrains.jet.lang.psi.JetDeclaration;
import org.jetbrains.jet.lang.psi.JetPsiUtil;
import org.jetbrains.jet.lang.resolve.lazy.ResolveSession;
import org.jetbrains.jet.util.slicedmap.ReadOnlySlice;
import org.jetbrains.jet.util.slicedmap.WritableSlice;

public class CliClassGenerationSupportTrace extends BindingTraceContext {
    private ResolveSession resolveSession;

    @Override
    public <K, V> void record(WritableSlice<K, V> slice, K key, V value) {
        if (slice == BindingContext.RESOLUTION_SCOPE || slice == BindingContext.TYPE_RESOLUTION_SCOPE) {
            // In the compiler there's no need to keep scopes
            return;
        }
        super.record(slice, key, value);
    }

    @Override
    public String toString() {
        return CliClassGenerationSupportTrace.class.getName();
    }

    public void setResolveSession(ResolveSession resolveSession) {
        this.resolveSession = resolveSession;
    }

    @Override
    public <K, V> V get(ReadOnlySlice<K, V> slice, K key) {
        if (resolveSession != null) {
            if (BindingContext.FUNCTION == slice || BindingContext.VARIABLE == slice) {
                if (super.get(slice, key) == null && key instanceof JetDeclaration) {
                    JetDeclaration jetDeclaration = (JetDeclaration) key;
                    if (!JetPsiUtil.isLocal(jetDeclaration)) {
                        resolveSession.resolveToDescriptor(jetDeclaration);
                    }
                }
            }
        }

        return super.get(slice, key);
    }
}
