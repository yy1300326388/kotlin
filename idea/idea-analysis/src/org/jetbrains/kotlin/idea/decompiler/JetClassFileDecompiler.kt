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

package org.jetbrains.kotlin.idea.decompiler

import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.compiled.ClassFileDecompilers
import com.intellij.util.containers.HashSet
import org.jetbrains.kotlin.idea.decompiler.stubBuilder.KotlinClsStubBuilder

public class JetClassFileDecompiler : ClassFileDecompilers.Full() {
//    // Sdk list can be outdated if some new jdks are added
//    val allJDKRoots = ProjectJdkTable.getInstance().getAllJdks().flatMapTo(HashSet<VirtualFile>()) { jdk ->
//        jdk.rootProvider.getFiles(OrderRootType.CLASSES).toList()
//    }

    private val stubBuilder = KotlinClsStubBuilder()

    override fun accepts(file: VirtualFile): Boolean {
//        if (file.getUrl().startsWith("jar://")) {
//            var rootFile = file
//            while (rootFile.getParent() != null) rootFile = rootFile.getParent()
//
//            if (VfsUtilCore.isUnder(rootFile, allJDKRoots)) return false
//        }

        return isKotlinJvmCompiledFile(file)
    }

    override fun getStubBuilder() = stubBuilder

    override fun createFileViewProvider(file: VirtualFile, manager: PsiManager, physical: Boolean)
            = JetClassFileViewProvider(manager, file, physical, isKotlinInternalCompiledFile(file))
}
