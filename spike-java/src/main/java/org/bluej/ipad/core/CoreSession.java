package org.bluej.ipad.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bluej.ipad.spike.InterpretedObject;
import org.bluej.ipad.spike.Interpreter;
import org.bluej.ipad.spike.Value;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The Java-side API the iPad UI drives: load compiled classes, list them, construct objects
 * onto the bench, invoke methods and read the console output.
 *
 * <p>It is deliberately synchronous and UI-free so it can be tested headlessly and wrapped by
 * a thin native bridge. Every failure the student's program can cause surfaces as
 * {@link org.bluej.ipad.spike.InterpretedThrowable}; failures of the request itself surface as
 * {@link IllegalArgumentException}.
 */
public final class CoreSession {
    private final Interpreter interpreter = new Interpreter();
    private final Map<String, byte[]> bytecode = new LinkedHashMap<>();
    private final Map<Integer, InterpretedObject> bench = new LinkedHashMap<>();
    private int nextHandle = 1;

    /** Loads compiled classes into the session, replacing any earlier class of the same name. */
    public synchronized void load(Map<String, byte[]> classes) {
        bytecode.putAll(classes);
        interpreter.load(classes);
    }

    public synchronized List<ClassInfo> classes() {
        List<ClassInfo> result = new ArrayList<>();
        bytecode.forEach((name, bytes) -> result.add(describe(name, bytes)));
        return List.copyOf(result);
    }

    private static ClassInfo describe(String name, byte[] bytes) {
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
        return new ClassInfo(name, List.copyOf(fields), List.copyOf(methods));
    }

    /** Constructs an object and puts it on the bench. */
    public synchronized BenchObject create(String className, List<Value> arguments) {
        requireLoaded(className);
        InterpretedObject object = interpreter.newObject(className, arguments.toArray(Value[]::new));
        int handle = nextHandle++;
        bench.put(handle, object);
        return new BenchObject(handle, className, object.fields());
    }

    public synchronized Value invoke(int handle, String method, List<Value> arguments) {
        InterpretedObject object = bench.get(handle);
        if (object == null) throw new IllegalArgumentException("no bench object with handle " + handle);
        return interpreter.invoke(object, method, arguments.toArray(Value[]::new));
    }

    public synchronized Value invokeStatic(String className, String method, List<Value> arguments) {
        requireLoaded(className);
        return interpreter.invokeStatic(className, method, arguments.toArray(Value[]::new));
    }

    /** Re-reads fields so the inspector reflects calls made since the object was created. */
    public synchronized BenchObject inspect(int handle) {
        InterpretedObject object = bench.get(handle);
        if (object == null) throw new IllegalArgumentException("no bench object with handle " + handle);
        return new BenchObject(handle, object.className(), object.fields());
    }

    /** The bench object with this handle as a call argument. */
    public synchronized Value reference(int handle) {
        InterpretedObject object = bench.get(handle);
        if (object == null) throw new IllegalArgumentException("no bench object with handle " + handle);
        return new Value.ObjectValue(object);
    }

    public synchronized List<BenchObject> bench() {
        List<BenchObject> result = new ArrayList<>();
        bench.forEach((handle, object) -> result.add(new BenchObject(handle, object.className(), object.fields())));
        return List.copyOf(result);
    }

    public String output() { return interpreter.output(); }

    public void clearOutput() { interpreter.clearOutput(); }

    public Set<String> loadedClasses() { return Set.copyOf(bytecode.keySet()); }

    private void requireLoaded(String className) {
        if (!bytecode.containsKey(className)) throw new IllegalArgumentException("class not loaded: " + className);
    }
}
