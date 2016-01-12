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

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.annotations.argumentValue
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import java.util.*

private val JAVA_DEPRECATED = FqName("java.lang.Deprecated")

interface Deprecation {
    val deprecationLevel: DeprecationLevelValue
    val message: String
    val target: DeclarationDescriptor

    fun exists() = deprecationLevel != DeprecationLevelValue.NONE
}

fun Deprecation.deprecatedByOverriddenMessage(): String? = if (this is DeprecatedByOverridden && exists()) additionalMessage() else null

private object NoDeprecation : Deprecation {
    override val deprecationLevel: DeprecationLevelValue
        get() = DeprecationLevelValue.NONE
    override val message: String
        get() = error("Should not be called")
    override val target: DeclarationDescriptor
        get() = error("Should not be called")
}


private data class DeprecatedByAnnotation(private val annotation: AnnotationDescriptor, override val target: DeclarationDescriptor) : Deprecation {
    override val deprecationLevel: DeprecationLevelValue
        get() {
            val level = annotation.argumentValue("level") as? ClassDescriptor

            return when (level?.name?.asString()) {
                "WARNING" -> DeprecationLevelValue.WARNING
                "ERROR" -> DeprecationLevelValue.ERROR
                "HIDDEN" -> DeprecationLevelValue.HIDDEN
                else -> DeprecationLevelValue.WARNING
            }
        }

    override val message: String
        get() = annotation.argumentValue("message") as? String ?: ""
}

private data class DeprecatedByOverridden(private val deprecations: Collection<Deprecation>) : Deprecation {
    init {
        assert(deprecations.isNotEmpty())
        assert(deprecations.none {
            it is DeprecatedByOverridden
        })
    }

    override val deprecationLevel: DeprecationLevelValue = deprecations.map(Deprecation::deprecationLevel).min()!!

    override val target: DeclarationDescriptor
        get() = deprecations.first().target

    override val message: String
        get() {
            val message = deprecations.filter { it.deprecationLevel == this.deprecationLevel }.first().message
            return "${additionalMessage()}. $message"
        }

    internal fun additionalMessage() = "Overrides deprecated member in '${DescriptorUtils.getContainingClass(target)!!.fqNameSafe.asString()}'"
}

fun DeclarationDescriptor.getDeprecation(): Deprecation {
    val deprecation = this.getDeprecationByAnnotation()
    if (deprecation != null) {
        return deprecation
    }

    if (this is CallableMemberDescriptor) {
        return deprecationByOverridden(this)
    }

    return NoDeprecation
}

private fun deprecationByOverridden(root: CallableMemberDescriptor): Deprecation {
    val visited = HashSet<CallableMemberDescriptor>()
    val deprecations = LinkedHashSet<Deprecation>()

    fun traverse(node: CallableMemberDescriptor) {
        if (node in visited) return

        visited.add(node)

        val deprecatedAnnotation = node.getDeprecationByAnnotation()
        val overriddenDescriptors = node.overriddenDescriptors
        when {
            deprecatedAnnotation != null -> {
                deprecations.add(deprecatedAnnotation)
            }
            overriddenDescriptors.isEmpty() -> {
                deprecations.add(NoDeprecation)
            }
            else -> {
                overriddenDescriptors.forEach { traverse(it) }
            }
        }
    }

    traverse(root)

    if (deprecations.isEmpty()) {
        return NoDeprecation
    }

    return DeprecatedByOverridden(deprecations)
}

private fun DeclarationDescriptor.getDeprecationByAnnotation(): DeprecatedByAnnotation? {
    val ownAnnotation = getDeclaredDeprecatedAnnotation(AnnotationUseSiteTarget.getAssociatedUseSiteTarget(this))
    if (ownAnnotation != null)
        return DeprecatedByAnnotation(ownAnnotation, this)

    when (this) {
        is ConstructorDescriptor -> {
            val classDescriptor = containingDeclaration
            val classAnnotation = classDescriptor.getDeclaredDeprecatedAnnotation()
            if (classAnnotation != null)
                return DeprecatedByAnnotation(classAnnotation, classDescriptor)
        }
        is PropertyAccessorDescriptor -> {
            val propertyDescriptor = correspondingProperty

            val target = if (this is PropertyGetterDescriptor) AnnotationUseSiteTarget.PROPERTY_GETTER else AnnotationUseSiteTarget.PROPERTY_SETTER
            val accessorAnnotationOnProperty = propertyDescriptor.getDeclaredDeprecatedAnnotation(target, false)
            if (accessorAnnotationOnProperty != null)
                return DeprecatedByAnnotation(accessorAnnotationOnProperty, this)

            val propertyAnnotation = propertyDescriptor.getDeclaredDeprecatedAnnotation()
            if (propertyAnnotation != null)
                return DeprecatedByAnnotation(propertyAnnotation, propertyDescriptor)

            val classDescriptor = containingDeclaration as? ClassDescriptor
            if (classDescriptor != null && classDescriptor.isCompanionObject) {
                val classAnnotation = classDescriptor.getDeclaredDeprecatedAnnotation()
                if (classAnnotation != null)
                    return DeprecatedByAnnotation(classAnnotation, classDescriptor)
            }
        }
    }
    return null
}

private fun DeclarationDescriptor.getDeclaredDeprecatedAnnotation(
        target: AnnotationUseSiteTarget? = null,
        findAnnotationsWithoutTarget: Boolean = true
): AnnotationDescriptor? {
    if (findAnnotationsWithoutTarget) {
        val annotations = annotations.findAnnotation(KotlinBuiltIns.FQ_NAMES.deprecated) ?: annotations.findAnnotation(JAVA_DEPRECATED)
        if (annotations != null) return annotations
    }

    if (target != null) {
        return Annotations.findUseSiteTargetedAnnotation(annotations, target, KotlinBuiltIns.FQ_NAMES.deprecated)
               ?: Annotations.findUseSiteTargetedAnnotation(annotations, target, JAVA_DEPRECATED)
    }

    return null
}

// values from kotlin.DeprecationLevel and NONE
enum class DeprecationLevelValue {
    NONE, WARNING, ERROR, HIDDEN
}

@Deprecated("Should be removed together with kotlin.HiddenDeclaration")
private val HIDDEN_ANNOTATION_FQ_NAME = FqName("kotlin.HiddenDeclaration")

fun DeclarationDescriptor.isHiddenInResolution(): Boolean {
    if (this is FunctionDescriptor && this.isHiddenToOvercomeSignatureClash) return true
    return annotations.findAnnotation(HIDDEN_ANNOTATION_FQ_NAME) != null
           || getDeprecation().deprecationLevel == DeprecationLevelValue.HIDDEN
}
