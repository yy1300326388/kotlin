/*
 * Copyright 2010-2013 JetBrains s.r.o.
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

package org.jetbrains.jet.lang.resolve.lazy;

import kotlin.Function0;
import org.jetbrains.jet.ConfigurationKind;
import org.jetbrains.jet.JetTestUtils;
import org.jetbrains.jet.KotlinTestWithEnvironmentManagement;
import org.jetbrains.jet.TestJdkKind;
import org.jetbrains.jet.analyzer.AnalysisResult;
import org.jetbrains.jet.cli.common.messages.AnalyzerWithCompilerReport;
import org.jetbrains.jet.cli.common.messages.MessageCollectorToString;
import org.jetbrains.jet.cli.jvm.compiler.JetCoreEnvironment;
import org.jetbrains.jet.lang.psi.JetFile;
import org.junit.Assert;

import java.io.File;
import java.util.List;

public class ResolveStdlibLoadingTest extends KotlinTestWithEnvironmentManagement {
    private static final File STD_LIB_SRC = new File("libraries/stdlib/src");
    private JetCoreEnvironment stdlibEnvironment;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        stdlibEnvironment = createEnvironmentWithJdk(ConfigurationKind.JDK_AND_ANNOTATIONS, TestJdkKind.FULL_JDK);
    }

    @Override
    protected void tearDown() throws Exception {
        stdlibEnvironment = null;
        super.tearDown();
    }

    protected void doTestForGivenFiles(final List<JetFile> files) {
        MessageCollectorToString collector = new MessageCollectorToString();
        AnalyzerWithCompilerReport compilerReport = new AnalyzerWithCompilerReport(collector);
        compilerReport.analyzeAndReport(files, new Function0<AnalysisResult>() {
            @Override
            public AnalysisResult invoke() {
                return LazyResolveTestUtil.resolveResult(files, stdlibEnvironment);
            }
        });

        Assert.assertTrue("There should be no errors in stdlib: " + collector.getString(), !compilerReport.hasErrors());
    }

    public void testStdLib() throws Exception {
        doTestForGivenFiles(
                JetTestUtils.loadToJetFiles(stdlibEnvironment, JetTestUtils.collectKtFiles(STD_LIB_SRC))
        );
    }
}
