package org.bluej.ipad.spike;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.eclipse.jdt.core.compiler.batch.BatchCompiler;
import org.junit.jupiter.api.Test;

class InMemoryCompilerTest {
    @Test
    void ecjCompilesSourceStringToBytecodeInProcess() throws Exception {
        var diagnostics = new StringWriter();
        var work = Files.createTempDirectory("bluej-ecj-test-");
        try {
            var sourcePath = work.resolve("example/Greeting.java");
            var output = work.resolve("classes");
            Files.createDirectories(sourcePath.getParent());
            Files.createDirectories(output);
            Files.writeString(sourcePath, """
                    package example;
                    public class Greeting { public static int answer() { return 6 * 7; } }
                    """, StandardCharsets.UTF_8);
            assertTrue(BatchCompiler.compile(new String[] {"-21", "-proc:none", "-d", output.toString(), sourcePath.toString()},
                    new PrintWriter(diagnostics), new PrintWriter(diagnostics), null), diagnostics.toString());
            byte[] bytecode = Files.readAllBytes(output.resolve("example/Greeting.class"));
            assertEquals((byte) 0xCA, bytecode[0]);
            assertEquals((byte) 0xFE, bytecode[1]);
        } finally {
            try (var paths = Files.walk(work)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
                });
            }
        }
    }

    @Test
    void ecjReportsInvalidSource() throws Exception {
        var diagnostics = new StringWriter();
        var work = Files.createTempDirectory("bluej-ecj-error-");
        try {
            var sourcePath = work.resolve("Broken.java");
            Files.writeString(sourcePath, "public class Broken { this is not Java }");
            assertFalse(BatchCompiler.compile(new String[] {"-21", "-proc:none", "-d", work.toString(), sourcePath.toString()},
                    new PrintWriter(diagnostics), new PrintWriter(diagnostics), null));
            assertFalse(diagnostics.toString().isBlank());
        } finally {
            try (var paths = Files.walk(work)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
                });
            }
        }
    }
}
