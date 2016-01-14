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

package org.jetbrains.kotlin.descriptors.annotations

import org.jetbrains.kotlin.descriptors.*

public enum class AnnotationUseSiteTarget(renderName: String? = null) {
    FIELD(),
    FILE(),
    PROPERTY(),
    PROPERTY_GETTER("get"),
    PROPERTY_SETTER("set"),
    RECEIVER(),
    CONSTRUCTOR_PARAMETER("param"),
    SETTER_PARAMETER("setparam"),
    PROPERTY_DELEGATE_FIELD("delegate");

    public val renderName: String = renderName ?: name().toLowerCase()

    public companion object {
        public fun getAssociatedUseSiteTarget(descriptor: DeclarationDescriptor): AnnotationUseSiteTarget? = when (descriptor) {
            is PropertyDescriptor -> PROPERTY
            is ValueParameterDescriptor -> CONSTRUCTOR_PARAMETER
            is PropertyGetterDescriptor -> PROPERTY_GETTER
            is PropertySetterDescriptor -> PROPERTY_SETTER
            else -> null
        }
    }
}