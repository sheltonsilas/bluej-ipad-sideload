package org.bluej.ipad.spike;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.jdt.core.compiler.batch.BatchCompiler;

/** Test-only compiler wrapper for ECJ's isolated technical feasibility proof. */
public final class InMemoryCompiler {
    /** {@code diagnostics} may contain warnings even when compilation succeeded. */
    public record Compilation(Map<String, byte[]> classes, String diagnostics, boolean success) {
        public boolean succeeded() { return success && !classes.isEmpty(); }
    }

    public Compilation compile(String className, String source) {
        var classes = new LinkedHashMap<String, byte[]>();
        var diagnostics = new StringWriter();
        var compiled = new boolean[1];
        Path work = null;
        try {
            work = Files.createTempDirectory("bluej-ecj-");
            Path sourceFile = work.resolve(className.replace('.', '/') + ".java");
            Path output = work.resolve("classes");
            Files.createDirectories(sourceFile.getParent());
            Files.createDirectories(output);
            Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
            boolean ok = BatchCompiler.compile(new String[]{"-21", "-proc:none", "-encoding", "UTF-8", "-d", output.toString(), sourceFile.toString()},
                    new PrintWriter(diagnostics), new PrintWriter(diagnostics), null);
            compiled[0] = ok;
            if (ok) try (var paths = Files.walk(output)) {
                for (Path file : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                    String name = output.relativize(file).toString().replace(java.io.File.separatorChar, '.');
                    classes.put(name.substring(0, name.length() - 6), Files.readAllBytes(file));
                }
            }
        } catch (Exception ex) {
            diagnostics.append(ex.getClass().getSimpleName()).append(": ").append(ex.getMessage());
        } finally {
            if (work != null) try (var paths = Files.walk(work)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
                });
            } catch (Exception ex) { diagnostics.append("\ncleanup failed: ").append(ex.getMessage()); }
        }
        return new Compilation(Map.copyOf(classes), diagnostics.toString(), compiled[0]);
    }
}
