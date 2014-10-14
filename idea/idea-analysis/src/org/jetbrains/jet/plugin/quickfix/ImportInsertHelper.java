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

package org.jetbrains.jet.plugin.quickfix;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.psi.JetImportDirective;
import org.jetbrains.jet.lang.resolve.ImportPath;
import org.jetbrains.jet.lang.resolve.name.FqName;

import java.util.List;

public abstract class ImportInsertHelper {
    public static ImportInsertHelper getInstance() {
        return ServiceManager.getService(ImportInsertHelper.class);
    }
    /**
     * Add import directive into the PSI tree for the given package.
     *
     * @param importFqn full name of the import
     * @param file File where directive should be added.
     * @param optimize Optimize existing imports before adding new one.
     */
    public abstract void addImportDirectiveIfNeeded(@NotNull FqName importFqn, @NotNull JetFile file, boolean optimize);

    public abstract void addImportDirectiveIfNeeded(@NotNull FqName importFqn, @NotNull JetFile file);

    public abstract void addImportDirectiveOrChangeToFqName(
            @NotNull FqName importFqn,
            @NotNull JetFile file,
            int refOffset,
            @NotNull PsiElement targetElement);

    public abstract void optimizeImportsIfNeeded(JetFile file);

    public abstract boolean isImportedWithDefault(@NotNull ImportPath importPath, @NotNull JetFile contextFile);

    public abstract boolean needImport(@NotNull FqName fqName, @NotNull JetFile file);

    public abstract boolean needImport(@NotNull ImportPath importPath, @NotNull JetFile file);

    public abstract boolean needImport(@NotNull ImportPath importPath, @NotNull JetFile file, List<JetImportDirective> importDirectives);
}
