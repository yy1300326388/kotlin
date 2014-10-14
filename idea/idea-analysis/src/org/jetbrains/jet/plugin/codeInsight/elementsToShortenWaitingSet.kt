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

package org.jetbrains.jet.plugin.codeInsight

import com.intellij.openapi.util.Key
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.jet.lang.psi.JetElement
import com.intellij.openapi.project.Project
import java.util.HashSet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.SmartPointerManager

private val ELEMENTS_TO_SHORTEN_KEY = Key.create<MutableSet<SmartPsiElementPointer<JetElement>>>("ELEMENTS_TO_SHORTEN_KEY")

internal fun Project.getElementsToShorten(createIfNeeded: Boolean): MutableSet<SmartPsiElementPointer<JetElement>>? {
    var elementsToShorten = getUserData(ELEMENTS_TO_SHORTEN_KEY)
    if (createIfNeeded && elementsToShorten == null) {
        elementsToShorten = HashSet()
        putUserData(ELEMENTS_TO_SHORTEN_KEY, elementsToShorten)
    }

    return elementsToShorten
}

internal fun Project.clearElementsToShorten() {
    putUserData(ELEMENTS_TO_SHORTEN_KEY, null)
}

public fun JetElement.addToShorteningWaitSet() {
    assert (ApplicationManager.getApplication()!!.isWriteAccessAllowed(), "Write access needed")
    val project = getProject()
    project.getElementsToShorten(true)!!.add(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(this))
}
