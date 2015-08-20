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

package org.jetbrains.kotlin.idea

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.smartPointers.SmartPointerAnchorProvider
import org.jetbrains.kotlin.asJava.KotlinLightClass
import org.jetbrains.kotlin.asJava.KotlinLightElement
import org.jetbrains.kotlin.asJava.KotlinLightMethod
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.psi.JetClassOrObject
import org.jetbrains.kotlin.psi.JetFunction

public class KotlinSmartPointerAnchorProvider : SmartPointerAnchorProvider() {
    override fun getAnchor(element: PsiElement): PsiElement? {
        if (element is KotlinLightClass || element is KotlinLightMethod) {
            return (element as KotlinLightElement<*, *>).getOrigin()
        }
        return null
    }

    override fun restoreElement(anchor: PsiElement): PsiElement? = when(anchor) {
        is JetClassOrObject -> LightClassUtil.getPsiClass(anchor)
        is JetFunction -> LightClassUtil.getLightClassMethod(anchor)
        else -> null
    }
}
