package org.bluej.ipad.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Compiles a whole project's sources in memory and returns bytecode plus javac diagnostics.
 *
 * <p>Sources are keyed by their project-relative path ({@code Counter.java},
 * {@code shapes/Circle.java}). Nothing is written to disk: the student's files live in the
 * app's document store, and the runtime only needs the class bytes.
 *
 * <p>Compilation uses the JDK's own javac, which is GPLv2+CE like BlueJ itself, rather than
 * the EPL-2.0 ECJ spike dependency. A build without a compiler (for example the GraalVM iOS
 * image) reports that explicitly instead of failing obscurely.
 */
public final class ProjectCompiler {

    public enum Severity { ERROR, WARNING, NOTE }

    /** One javac message, positioned in a project file. */
    public record Diagnostic(String path, int line, int column, String message, Severity severity) {
        public Diagnostic {
            path = path == null ? "" : path;
            message = message == null ? "" : message;
            line = Math.max(1, line);
            column = Math.max(1, column);
        }
    }

    public record Result(boolean success, Map<String, byte[]> classes, List<Diagnostic> diagnostics) {
        public Result {
            classes = Map.copyOf(classes);
            diagnostics = List.copyOf(diagnostics);
        }

        static Result failure(String message) {
            return new Result(false, Map.of(),
                    List.of(new Diagnostic("", 1, 1, message, Severity.ERROR)));
        }
    }

    /** Name of the top-level type a source file declares, or {@code null} if none was found. */
    public static String declaredTypeName(String source) {
        if (source == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\b(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)")
                .matcher(stripCommentsAndStrings(source));
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Declared package of a source file, or the empty string for the default package. */
    public static String declaredPackage(String source) {
        if (source == null) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\bpackage\\s+([A-Za-z_$][A-Za-z0-9_$.]*)\\s*;")
                .matcher(stripCommentsAndStrings(source));
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String stripCommentsAndStrings(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) i++;
                i = Math.min(n, i + 2);
                out.append(' ');
            } else if (c == '"' || c == '\'') {
                char quote = c;
                i++;
                while (i < n) {
                    char d = source.charAt(i);
                    if (d == '\\') { i += 2; continue; }
                    i++;
                    if (d == quote) break;
                }
                out.append(' ');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    public Result compile(Map<String, String> sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return Result.failure(
                    "No Java compiler is available in this build, so the project cannot be compiled here.");
        }
        if (sources.isEmpty()) {
            return new Result(true, Map.of(), List.of());
        }

        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        StandardJavaFileManager standard =
                compiler.getStandardFileManager(collector, Locale.ROOT, StandardCharsets.UTF_8);
        Map<String, byte[]> classes = new LinkedHashMap<>();
        try (MemoryFileManager files = new MemoryFileManager(standard)) {
            List<JavaFileObject> units = new ArrayList<>();
            for (Map.Entry<String, String> entry : sources.entrySet()) {
                units.add(new SourceFile(normalize(entry.getKey()),
                        entry.getValue() == null ? "" : entry.getValue()));
            }
            List<String> options = List.of("-proc:none", "-g", "-nowarn", "-Xlint:none", "-encoding", "UTF-8");
            JavaCompiler.CompilationTask task = compiler.getTask(
                    new java.io.StringWriter(), files, collector, options, null, units);
            boolean success = Boolean.TRUE.equals(task.call());
            classes.putAll(files.written());

            List<Diagnostic> diagnostics = new ArrayList<>();
            for (javax.tools.Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
                diagnostics.add(new Diagnostic(
                        pathOf(diagnostic.getSource()),
                        (int) Math.max(1, diagnostic.getLineNumber()),
                        (int) Math.max(1, diagnostic.getColumnNumber()),
                        diagnostic.getMessage(Locale.ROOT),
                        switch (diagnostic.getKind()) {
                            case ERROR -> Severity.ERROR;
                            case WARNING, MANDATORY_WARNING -> Severity.WARNING;
                            default -> Severity.NOTE;
                        }));
            }
            // A failed pass can still emit bytes for units that did compile; the app keeps its
            // last good class files, so a failed pass hands back nothing runnable.
            if (!success) classes.clear();
            return new Result(success, classes, diagnostics);
        } catch (IOException | RuntimeException failure) {
            return Result.failure("Compilation failed: " + failure.getMessage());
        }
    }

    private static String normalize(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        return normalized;
    }

    private static String pathOf(JavaFileObject source) {
        if (source == null) return "";
        URI uri = source.toUri();
        String path = uri.getPath() == null ? uri.toString() : uri.getPath();
        while (path.startsWith("/")) path = path.substring(1);
        return path;
    }

    /** A source file backed by a string. */
    private static final class SourceFile extends SimpleJavaFileObject {
        private final String text;

        SourceFile(String path, String text) {
            super(URI.create("string:///" + path.replace('\\', '/')), Kind.SOURCE);
            this.text = text;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }

    /** A class file written to a byte array instead of the file system. */
    private static final class ClassFile extends SimpleJavaFileObject {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        ClassFile(String className) {
            super(URI.create("bytes:///" + className.replace('.', '/') + ".class"), Kind.CLASS);
        }

        @Override
        public OutputStream openOutputStream() {
            return bytes;
        }

        @Override
        public InputStream openInputStream() {
            return new ByteArrayInputStream(bytes.toByteArray());
        }

        byte[] read() { return bytes.toByteArray(); }
    }

    private static final class MemoryFileManager
            extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, ClassFile> outputs = new LinkedHashMap<>();

        MemoryFileManager(StandardJavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className,
                JavaFileObject.Kind kind, FileObject sibling) {
            ClassFile file = new ClassFile(className);
            outputs.put(className, file);
            return file;
        }

        Map<String, byte[]> written() {
            Map<String, byte[]> result = new LinkedHashMap<>();
            outputs.forEach((name, file) -> result.put(name, file.read()));
            return result;
        }
    }
}
