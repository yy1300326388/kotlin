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

package org.jetbrains.jet.renderer

import org.jetbrains.jet.lang.resolve.lazy.JvmResolveUtil
import org.jetbrains.jet.lang.resolve.BindingContext
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor
import org.jetbrains.jet.lang.psi.JetVisitorVoid
import org.jetbrains.jet.lang.psi.JetElement
import org.jetbrains.jet.lang.descriptors.ClassDescriptor
import com.intellij.openapi.editor.impl.DocumentImpl
import org.jetbrains.jet.JetTestUtils
import org.jetbrains.jet.JetLiteFixture
import java.util.ArrayList
import com.intellij.psi.PsiElement
import org.jetbrains.jet.cli.jvm.compiler.JetCoreEnvironment
import org.jetbrains.jet.ConfigurationKind
import org.jetbrains.jet.JetTestCaseBuilder
import com.intellij.testFramework.UsefulTestCase
import java.io.File

public abstract class AbstractDescriptorRendererTest : JetLiteFixture() {
    public fun doTest(path: String) {
        val file = File(path)
        val psiFile = createPsiFile(null, file.getName(), loadFile(path))
        val analysisResult = JvmResolveUtil.analyzeOneFileWithJavaIntegration(psiFile)
        val bindingContext = analysisResult.bindingContext

        val descriptors = ArrayList<DeclarationDescriptor>()

        val fqName = psiFile.getPackageFqName()
        if (!fqName.isRoot()) {
            val packageDescriptor = analysisResult.moduleDescriptor.getPackage(fqName)
            descriptors.add(packageDescriptor)
        }

        psiFile.acceptChildren(object : JetVisitorVoid() {
            override fun visitJetElement(element: JetElement) {
                val descriptor = bindingContext.get<PsiElement, DeclarationDescriptor>(BindingContext.DECLARATION_TO_DESCRIPTOR, element)
                if (descriptor != null) {
                    descriptors.add(descriptor)
                    if (descriptor is ClassDescriptor) {
                        descriptors.addAll(descriptor.getConstructors())
                    }
                }
                element.acceptChildren(this)
            }
        })

        val renderedDescriptors = descriptors.map { DescriptorRenderer.FQ_NAMES_IN_TYPES.render(it) }.joinToString(separator = "\n")

        val document = DocumentImpl(psiFile.getText())
        UsefulTestCase.assertSameLines(JetTestUtils.getLastCommentedLines(document), renderedDescriptors.toString())
    }

    override fun createEnvironment(): JetCoreEnvironment {
        return createEnvironmentWithMockJdk(ConfigurationKind.JDK_ONLY)
    }

    override fun getTestDataPath() = JetTestCaseBuilder.getHomeDirectory()
}


