package org.jetbrains.kotlin.util

import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.incremental.KotlinLookupLocation
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.incremental.*
import org.jetbrains.kotlin.types.DeferredType
import org.jetbrains.kotlin.types.typeUtil.isUnit

fun LookupTracker.record(expression: KtExpression, type: KotlinType) {
    if (type.isError || type.isUnit()) return

    val typeDescriptor = type.constructor.declarationDescriptor ?: return
    val scopeDescriptor = typeDescriptor.containingDeclaration

    // Scope descriptor is function descriptor only when type is local
    // Lookups for local types are not needed since all usages are compiled with the type
    if (scopeDescriptor !is FunctionDescriptor &&
        scopeDescriptor !is PropertyDescriptor
    ) {
        record(KotlinLookupLocation(expression), scopeDescriptor, typeDescriptor.name)
    }

    type.arguments.filter { !it.isStarProjection }.forEach { record(expression, it.type) }
}

