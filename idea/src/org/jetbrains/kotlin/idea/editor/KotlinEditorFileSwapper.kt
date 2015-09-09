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

package org.jetbrains.kotlin.idea.editor

import com.intellij.openapi.fileEditor.impl.EditorFileSwapper
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptor
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.decompiler.JetClsFile
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration

public class KotlinEditorFileSwapper : EditorFileSwapper() {
    override fun getFileToSwapTo(project: Project, editor: EditorWithProviderComposite): Pair<VirtualFile, Int>? {
        val file = editor.file
        val sourceFile = findSourceFile(project, file) ?: return null

        var position: Int? = null

        val oldEditor = EditorFileSwapper.findSinglePsiAwareEditor(editor.editors)
        if (oldEditor != null) {
            val clsFile = PsiManager.getInstance(project).findFile(file) as JetClsFile?
            assert(clsFile != null)

            val offset = oldEditor.editor.caretModel.offset

            val elementAt = clsFile!!.findElementAt(offset)
            var member = PsiTreeUtil.getParentOfType(elementAt, KtDeclaration::class.java, false)

            if (member is KtClass) {
                var isFirstMember = true

                var e: PsiElement? = member.firstChild
                while (e != null) {
                    if (e is KtDeclaration) {
                        if (offset < e.textRange.endOffset) {
                            if (!isFirstMember) {
                                member = e
                            }

                            break
                        }

                        isFirstMember = false
                    }
                    e = e.nextSibling
                }
            }

            if (member != null) {
                val navigationElement = member.navigationElement
                if (Comparing.equal(navigationElement.containingFile.virtualFile, sourceFile)) {
                    position = navigationElement.textOffset
                }
            }
        }

        return Pair.create<VirtualFile, Int>(sourceFile, position)
    }

    companion object {

        public fun findSourceFile(project: Project, file: VirtualFile): VirtualFile? {
            val psiFile = PsiManager.getInstance(project).findFile(file)
            if (psiFile is JetClsFile) {
                val declarations = psiFile.declarations
                if (declarations.isNotEmpty()) {
                    val first = declarations.first()
                    val descriptor = first.resolveToDescriptor()
                    val declaration = DescriptorToSourceUtilsIde.getAnyDeclaration(project, descriptor)
                    if (declaration != null) {
                        return declaration.containingFile.virtualFile
                    }
                }
            }

            return null
        }
    }
}