package org.bluej.ipad.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.bluej.ipad.spike.InterpretedObject;
import org.bluej.ipad.spike.InterpretedThrowable;
import org.bluej.ipad.spike.Interpreter;
import org.bluej.ipad.spike.Value;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The Java-side API the iPad UI drives: a project's sources, compilation, the class metadata
 * for the diagram, and the object bench with method calls, inspection and console output.
 *
 * <p>It is deliberately synchronous and UI-free so it can be tested headlessly and wrapped by
 * a thin native bridge. Every failure the student's program can cause surfaces as
 * {@link org.bluej.ipad.spike.InterpretedThrowable}; failures of the request itself surface as
 * {@link IllegalArgumentException}.
 */
public final class CoreSession {

    /** What one compile pass produced. */
    public record CompileReport(boolean success, long revision, List<ClassInfo> classes,
            List<ProjectCompiler.Diagnostic> diagnostics) {}

    /**
     * The state of one bench call: either it finished with a value, or the program is parked
     * waiting for a line of terminal input. {@link #supplyInput(String)} resumes it.
     */
    public record CallState(boolean needsInput, Value value) {}

    private record BenchEntry(int handle, String name, InterpretedObject object) {}

    private final Interpreter interpreter;
    private final DebugSession debug;
    private final ProjectCompiler compiler = new ProjectCompiler();
    private final Map<String, String> sources = new LinkedHashMap<>();
    private final Map<String, byte[]> bytecode = new LinkedHashMap<>();
    private final Map<Integer, BenchEntry> bench = new LinkedHashMap<>();
    private List<ProjectCompiler.Diagnostic> diagnostics = List.of();
    private int nextHandle = 1;
    private long revision;
    private final Object runMonitor = new Object();
    private volatile Run activeRun;

    public CoreSession() {
        this(new Interpreter());
    }

    public CoreSession(Interpreter interpreter) {
        this.interpreter = interpreter;
        this.debug = new DebugSession(interpreter);
        interpreter.setInput(this::readInput);
    }

    // ---------------------------------------------------------------- interactive calls

    /**
     * One call in flight. The interpreter runs on its own thread so the request that started
     * it can return "needs input" and a later request can feed a line without re-entering
     * the interpreter.
     */
    /** Thrown on the interpreter's thread when the student stops a call that is waiting. */
    private static final class Cancelled extends RuntimeException {
        Cancelled() { super("call cancelled", null, false, false); }
    }

    private final class Run {
        private final Object lock = new Object();
        private final java.util.ArrayDeque<String> lines = new java.util.ArrayDeque<>();
        private boolean waiting;
        private boolean closed;
        private boolean cancelled;
        private boolean done;
        private Value value;
        private Throwable failure;

        String readLine() {
            synchronized (lock) {
                while (lines.isEmpty() && !closed && !cancelled) {
                    waiting = true;
                    lock.notifyAll();
                    try {
                        lock.wait();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("input was interrupted");
                    } finally {
                        waiting = false;
                    }
                }
                if (cancelled) throw new Cancelled();
                return lines.pollFirst();
            }
        }
    }

    private String readInput() {
        if (debug.active()) {
            throw new UnsupportedOperationException(
                    "reading terminal input during a debug run is not supported yet");
        }
        Run run;
        synchronized (runMonitor) { run = activeRun; }
        if (run == null) {
            throw new IllegalStateException("Java input is only available while a method is running");
        }
        return run.readLine();
    }

    private CallState run(java.util.concurrent.Callable<Value> body) {
        Run run = new Run();
        synchronized (this) {
            requireNoDebugSession();
            if (activeRun != null) throw new IllegalStateException("a program is already waiting for input");
            activeRun = run;
        }
        Thread worker = new Thread(() -> {
            try {
                run.value = body.call();
            } catch (Throwable failure) {
                run.failure = failure;
            } finally {
                synchronized (runMonitor) {
                    if (activeRun == run) activeRun = null;
                }
                synchronized (run.lock) {
                    run.done = true;
                    run.lock.notifyAll();
                }
            }
        }, "bluej-call");
        worker.setDaemon(true);
        worker.start();
        return await(run);
    }

    private CallState await(Run run) {
        synchronized (run.lock) {
            while (!run.done && !run.waiting) {
                try {
                    run.lock.wait();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while waiting for the program", interrupted);
                }
            }
            if (!run.done) return new CallState(true, null);
            if (run.failure instanceof InterpretedThrowable thrown) throw thrown;
            if (run.failure instanceof RuntimeException runtime) throw runtime;
            if (run.failure instanceof Error error) throw error;
            if (run.failure != null) throw new IllegalStateException(run.failure);
            return new CallState(false, run.value);
        }
    }

    /**
     * Feeds one line from the terminal to a program that asked for input, then waits until it
     * finishes or asks for another line. {@code null} closes standard input (end of file).
     */
    public CallState supplyInput(String text) {
        Run run;
        synchronized (runMonitor) { run = activeRun; }
        if (run == null) throw new IllegalArgumentException("no program is waiting for input");
        synchronized (run.lock) {
            if (!run.waiting) throw new IllegalArgumentException("the program is not asking for input");
            // Claim the prompt: the next "waiting" this request sees must be a new read, not
            // the one it is about to satisfy.
            run.waiting = false;
            if (text == null) {
                run.closed = true;
            } else {
                String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
                int start = 0;
                boolean queued = false;
                for (int i = 0; i < normalized.length(); i++) {
                    if (normalized.charAt(i) != '\n') continue;
                    run.lines.add(normalized.substring(start, i));
                    start = i + 1;
                    queued = true;
                }
                if (start < normalized.length()) {
                    run.lines.add(normalized.substring(start));
                    queued = true;
                }
                // Submitting an empty field means one empty line, which nextLine reads as "".
                if (!queued) run.lines.add("");
            }
            run.lock.notifyAll();
        }
        return await(run);
    }

    private void requireNoRun() {
        if (activeRun != null) throw new IllegalStateException("the program is waiting for input");
    }

    /**
     * Stops a call that is parked on a terminal read. The interpreter thread unwinds with a
     * private exception, so the session is usable again immediately.
     */
    public void cancelCall() {
        Run run;
        synchronized (runMonitor) { run = activeRun; }
        if (run == null) return;
        synchronized (run.lock) {
            if (!run.waiting) return;
            run.cancelled = true;
            run.lock.notifyAll();
            while (!run.done) {
                try {
                    run.lock.wait();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    // ---------------------------------------------------------------- debugger

    /**
     * Runs a static method (usually {@code main}) under the debugger. Blocks until the program
     * pauses at a breakpoint or step, or finishes.
     */
    public synchronized DebugSession.Snapshot debugStart(String className, String method,
            List<Value> arguments) {
        if (debug.active()) throw new IllegalStateException("a debug session is already running");
        requireRunnable(className);
        return debug.start(className, method, arguments);
    }

    /** Resumes a paused program and blocks until it pauses again or finishes. */
    public synchronized DebugSession.Snapshot debugCommand(String action) {
        return debug.command(action);
    }

    public DebugSession.Snapshot debugSnapshot() {
        return debug.snapshot();
    }

    public void setBreakpoints(List<String> points) {
        debug.setBreakpoints(points);
    }

    public List<String> breakpoints() {
        return debug.breakpoints();
    }

    public boolean debugging() {
        return debug.active();
    }

    private void requireNoDebugSession() {
        if (debug.active()) {
            throw new IllegalStateException("stop the debugger before changing or running the program");
        }
    }

    // ---------------------------------------------------------------- sources

    /** Adds or replaces a source file, keyed by project-relative path. */
    public synchronized long setSource(String path, String text) {
        requireNoRun();
        sources.put(normalize(path), text == null ? "" : text);
        revision++;
        return revision;
    }

    public synchronized long deleteSource(String path) {
        requireNoRun();
        sources.remove(normalize(path));
        revision++;
        return revision;
    }

    public synchronized Map<String, String> sources() {
        return Map.copyOf(sources);
    }

    public synchronized String source(String path) {
        return sources.get(normalize(path));
    }

    /** The standard BlueJ class body for a new file, so a new class is already runnable. */
    public static String newClassSource(String simpleName, String kind, String packageName) {
        String packageLine = packageName == null || packageName.isBlank()
                ? "" : "package " + packageName + ";\n\n";
        String declaration = switch (kind == null ? "class" : kind) {
            case "interface" -> "public interface " + simpleName;
            case "abstract" -> "public abstract class " + simpleName;
            case "enum" -> "public enum " + simpleName;
            default -> "public class " + simpleName;
        };
        if (declaration.startsWith("public interface") || declaration.startsWith("public enum")) {
            return packageLine + "/**\n * Write a description of " + kind + " " + simpleName + " here.\n */\n"
                    + declaration + "\n{\n    // add constants and methods here\n}\n";
        }
        return packageLine
                + "/**\n * Write a description of class " + simpleName + " here.\n *\n"
                + " * @author (your name)\n * @version (a version number or a date)\n */\n"
                + declaration + "\n{\n    // instance variables - replace the example below with your own\n"
                + "    private int value;\n\n    /**\n     * Constructor for objects of class " + simpleName + "\n     */\n"
                + "    public " + simpleName + "()\n    {\n        value = 0;\n    }\n\n"
                + "    /**\n     * An example of a method - replace this comment with your own\n     */\n"
                + "    public int getValue()\n    {\n        return value;\n    }\n\n"
                + "    public void setValue(int newValue)\n    {\n        value = newValue;\n    }\n}\n";
    }

    // ---------------------------------------------------------------- compilation

    /** Compiles every source, keeps the last good classes on failure and reports each class. */
    public synchronized CompileReport compile() {
        requireNoDebugSession();
        requireNoRun();
        ProjectCompiler.Result result = compiler.compile(new LinkedHashMap<>(sources));
        diagnostics = result.diagnostics();
        if (result.success()) {
            bytecode.clear();
            bytecode.putAll(result.classes());
            interpreter.reset();
            interpreter.load(result.classes());
            bench.clear();
            revision++;
        }
        return new CompileReport(result.success(), revision, classes(), diagnostics);
    }

    /** Loads precompiled classes (used by the native shell self-test and the test suite). */
    public synchronized void load(Map<String, byte[]> classes) {
        bytecode.putAll(classes);
        interpreter.load(classes);
        diagnostics = List.of();
    }

    private static String simpleFileName(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }

    // ---------------------------------------------------------------- class model

    public synchronized List<ClassInfo> classes() {
        Set<String> failingSources = new java.util.HashSet<>();
        for (ProjectCompiler.Diagnostic diagnostic : diagnostics) {
            if (diagnostic.severity() == ProjectCompiler.Severity.ERROR) failingSources.add(diagnostic.path());
        }
        Map<String, String> typePaths = typePaths();
        Map<String, ClassInfo> result = new LinkedHashMap<>();

        new TreeSet<>(bytecode.keySet()).forEach(name -> {
            if (name.contains("$")) return;
            String path = typePaths.getOrDefault(name, "");
            boolean broken = failingSources.contains(path);
            result.put(name, describe(name, bytecode.get(name), path, !broken, broken));
        });

        typePaths.forEach((type, path) -> {
            if (result.containsKey(type)) return;
            String text = sources.getOrDefault(path, "");
            result.put(type, new ClassInfo(type, kindOf(text), null, path, false,
                    failingSources.contains(path), List.of(), List.of()));
        });
        return List.copyOf(result.values());
    }

    /** Binary type name to the source path that declares it. */
    private Map<String, String> typePaths() {
        Map<String, String> paths = new LinkedHashMap<>();
        sources.forEach((path, text) -> {
            String simple = ProjectCompiler.declaredTypeName(text);
            if (simple == null) {
                // A brand new empty file still deserves a card; fall back to the file name.
                simple = simpleFileName(path);
                if (simple.endsWith(".java")) simple = simple.substring(0, simple.length() - 5);
            }
            String pkg = ProjectCompiler.declaredPackage(text);
            paths.put(pkg.isEmpty() ? simple : pkg + "." + simple, path);
        });
        return paths;
    }

    private static String kindOf(String source) {
        String cleaned = source == null ? "" : source;
        if (cleaned.matches("(?s).*\\binterface\\s+[A-Za-z_$][A-Za-z0-9_$]*.*")) return "interface";
        if (cleaned.matches("(?s).*\\benum\\s+[A-Za-z_$][A-Za-z0-9_$]*.*")) return "enum";
        if (cleaned.matches("(?s).*\\brecord\\s+[A-Za-z_$][A-Za-z0-9_$]*.*")) return "record";
        return "class";
    }

    private static ClassInfo describe(String name, byte[] bytes, String path, boolean compiled, boolean error) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        List<ClassInfo.Field> fields = new ArrayList<>();
        for (FieldNode field : node.fields) {
            fields.add(new ClassInfo.Field(field.name, Type.getType(field.desc).getClassName(),
                    (field.access & Opcodes.ACC_STATIC) != 0));
        }
        List<ClassInfo.Method> methods = new ArrayList<>();
        for (MethodNode method : node.methods) {
            if (method.name.startsWith("<")) continue;
            List<String> parameters = new ArrayList<>();
            for (Type parameter : Type.getArgumentTypes(method.desc)) parameters.add(parameter.getClassName());
            methods.add(new ClassInfo.Method(method.name, List.copyOf(parameters),
                    Type.getReturnType(method.desc).getClassName(),
                    (method.access & Opcodes.ACC_STATIC) != 0));
        }
        String kind;
        if ((node.access & Opcodes.ACC_ANNOTATION) != 0) kind = "annotation";
        else if ((node.access & Opcodes.ACC_INTERFACE) != 0) kind = "interface";
        else if ((node.access & Opcodes.ACC_ENUM) != 0) kind = "enum";
        else if ((node.access & Opcodes.ACC_RECORD) != 0) kind = "record";
        else kind = "class";
        String superclass = node.superName == null ? null : node.superName.replace('/', '.');
        return new ClassInfo(name, kind, superclass, path, compiled, error,
                List.copyOf(fields), List.copyOf(methods));
    }

    // ---------------------------------------------------------------- bench

    /** Constructs an object and puts it on the bench under a BlueJ-style instance name. */
    public synchronized BenchObject create(String className, List<Value> arguments) {
        requireNoDebugSession();
        requireNoRun();
        ClassInfo info = requireRunnable(className);
        InterpretedObject object = interpreter.newObject(className, arguments.toArray(Value[]::new));
        int handle = nextHandle++;
        String name = instanceName(info.name());
        bench.put(handle, new BenchEntry(handle, name, object));
        return new BenchObject(handle, name, className, object.fields());
    }

    private String instanceName(String className) {
        String simple = className.substring(className.lastIndexOf('.') + 1);
        String base = simple.isEmpty() ? "object" : Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
        int index = 1;
        while (benchNameTaken(base + index)) index++;
        return base + index;
    }

    private boolean benchNameTaken(String name) {
        return bench.values().stream().anyMatch(entry -> entry.name().equals(name));
    }

    /** Calls an instance method; returns either its value or a request for terminal input. */
    public CallState invoke(int handle, String method, List<Value> arguments) {
        InterpretedObject object;
        synchronized (this) {
            requireNoDebugSession();
            BenchEntry entry = bench.get(handle);
            if (entry == null) throw new IllegalArgumentException("no bench object with handle " + handle);
            object = entry.object();
        }
        return run(() -> interpreter.invoke(object, method, arguments.toArray(Value[]::new)));
    }

    /** Calls a static method; returns either its value or a request for terminal input. */
    public CallState invokeStatic(String className, String method, List<Value> arguments) {
        synchronized (this) {
            requireNoDebugSession();
            requireRunnable(className);
        }
        return run(() -> interpreter.invokeStatic(className, method, arguments.toArray(Value[]::new)));
    }

    /** Re-reads fields so the inspector reflects calls made since the object was created. */
    public synchronized BenchObject inspect(int handle) {
        requireNoRun();
        BenchEntry entry = bench.get(handle);
        if (entry == null) throw new IllegalArgumentException("no bench object with handle " + handle);
        return new BenchObject(handle, entry.name(), entry.object().className(), entry.object().fields());
    }

    /** The bench object with this handle as a call argument. */
    public synchronized Value reference(int handle) {
        BenchEntry entry = bench.get(handle);
        if (entry == null) throw new IllegalArgumentException("no bench object with handle " + handle);
        return new Value.ObjectValue(entry.object());
    }

    public synchronized List<BenchObject> bench() {
        List<BenchObject> result = new ArrayList<>();
        bench.values().forEach(entry -> result.add(
                new BenchObject(entry.handle(), entry.name(), entry.object().className(), entry.object().fields())));
        return List.copyOf(result);
    }

    public synchronized void removeObject(int handle) {
        requireNoRun();
        if (bench.remove(handle) == null) {
            throw new IllegalArgumentException("no bench object with handle " + handle);
        }
    }

    public synchronized void clearBench() {
        requireNoRun();
        bench.clear();
    }

    public synchronized void reset() {
        requireNoDebugSession();
        requireNoRun();
        sources.clear();
        bytecode.clear();
        bench.clear();
        interpreter.reset();
        diagnostics = List.of();
        revision++;
    }

    public String output() { return interpreter.output(); }

    public void clearOutput() { interpreter.clearOutput(); }

    public Set<String> loadedClasses() { return Set.copyOf(bytecode.keySet()); }

    public synchronized List<ProjectCompiler.Diagnostic> diagnostics() { return diagnostics; }

    public synchronized long revision() { return revision; }

    private ClassInfo requireRunnable(String className) {
        List<ClassInfo> all = classes();
        for (ClassInfo info : all) {
            if (!info.name().equals(className)) continue;
            if (info.error()) throw new IllegalArgumentException("class has compile errors: " + className);
            if (!info.compiled()) throw new IllegalArgumentException("class is not compiled: " + className);
            return info;
        }
        throw new IllegalArgumentException("class not loaded: " + className);
    }

    private static String normalize(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        return normalized;
    }
}
