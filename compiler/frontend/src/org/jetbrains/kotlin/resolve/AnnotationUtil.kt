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

package org.jetbrains.kotlin.resolve.annotations

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PropertyAccessorDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.constants.ErrorValue

public fun DeclarationDescriptor.hasJvmStaticAnnotation(): Boolean {
    return getAnnotations().findAnnotation(FqName("kotlin.jvm.JvmStatic")) != null
}

private val JVM_SYNTHETIC_ANNOTATION_FQ_NAME = FqName("kotlin.jvm.JvmSynthetic")

fun DeclarationDescriptor.hasJvmSyntheticAnnotation() = findJvmSyntheticAnnotation() != null

fun DeclarationDescriptor.findJvmSyntheticAnnotation() =
        DescriptorUtils.getAnnotationByFqName(annotations, JVM_SYNTHETIC_ANNOTATION_FQ_NAME)

public fun CallableDescriptor.isPlatformStaticInObjectOrClass(): Boolean =
        isPlatformStaticIn { DescriptorUtils.isNonCompanionObject(it) || DescriptorUtils.isClass(it) || DescriptorUtils.isEnumClass(it) }

public fun CallableDescriptor.isPlatformStaticInCompanionObject(): Boolean =
        isPlatformStaticIn { DescriptorUtils.isCompanionObject(it) }

private fun CallableDescriptor.isPlatformStaticIn(predicate: (DeclarationDescriptor) -> Boolean): Boolean =
        when (this) {
            is PropertyAccessorDescriptor -> {
                val propertyDescriptor = getCorrespondingProperty()
                predicate(propertyDescriptor.getContainingDeclaration()) &&
                (hasJvmStaticAnnotation() || propertyDescriptor.hasJvmStaticAnnotation())
            }
            else -> predicate(getContainingDeclaration()) && hasJvmStaticAnnotation()
        }

fun AnnotationDescriptor.argumentValue(parameterName: String): Any? {
    val constant: ConstantValue<*>? = allValueArguments.entries
            .singleOrNull { it.key.name.asString() == parameterName }
            ?.value

    if (constant == null || constant is ErrorValue)
        return null

    return constant.value
}