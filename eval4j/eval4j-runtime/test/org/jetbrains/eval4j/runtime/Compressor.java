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

package org.jetbrains.eval4j.runtime;

import org.junit.Assert;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Compressor {
    public static void main(String[] args) throws IOException {
        File dir = new File("out/production/eval4j.runtime/org/jetbrains/eval4j/runtime");

        File classFile = new File(dir, "BitDecoding.class");

        File noDebugClassFile = new File(dir, "1.class");
        if (noDebugClassFile.delete()) {
            System.out.println("Deleted: " + noDebugClassFile);
        }
        File gzipFile = new File(dir, "BitDecoding.gzip");
        gzipFile.delete();

        stripDebugInfo(classFile, noDebugClassFile);

        System.out.println("No debug size: " + noDebugClassFile.length());

        compressAndTest(noDebugClassFile, gzipFile);
    }

    private static void stripDebugInfo(File classFile, File noDebugClassFile) throws IOException {
        ClassWriter classWriter = new ClassWriter(0);
        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM4, classWriter) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                // The name "0/0" makes it virtually impossible that the class is already loaded
                // or the package is signed by anybody else
                super.visit(version, access, "0/0", signature, superName, interfaces);
            }
        };

        new ClassReader(new FileInputStream(classFile)).accept(classVisitor, ClassReader.SKIP_DEBUG);

        readAll(new ByteArrayInputStream(classWriter.toByteArray()), new FileOutputStream(noDebugClassFile));
    }

    private static void compressAndTest(File classFile, File gzipFile) throws IOException {
        compress(classFile, gzipFile);

        System.out.println("Size: " + gzipFile.length());

        // Test

        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        readAll(new FileInputStream(classFile), expected);

        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        readAll(new GZIPInputStream(new FileInputStream(gzipFile)), actual);

        Assert.assertArrayEquals(expected.toByteArray(), actual.toByteArray());
    }

    private static void compress(File classFile, File gzipFile) throws IOException {
        FileOutputStream out = new FileOutputStream(gzipFile);
        GZIPOutputStream zip = new GZIPOutputStream(out);

        FileInputStream in = new FileInputStream(classFile);
        readAll(in, zip);
        zip.close();
    }

    private static void readAll(InputStream from, OutputStream to) throws IOException {
        byte[] buf = new byte[4 * 1024];
        int c;
        while ((c = from.read(buf)) != -1) {
            to.write(buf, 0, c);
        }
    }
}
