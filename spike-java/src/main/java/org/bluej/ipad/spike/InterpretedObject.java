package org.bluej.ipad.spike;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** An object instance whose fields and methods remain inside the interpreter. */
public final class InterpretedObject {
    private final String className;
    private final Map<String, Value> fields = new LinkedHashMap<>();

    InterpretedObject(String className) { this.className = Objects.requireNonNull(className); }
    public String className() { return className; }
    public Value getField(String name) { return fields.getOrDefault(name, Value.NullValue.INSTANCE); }
    void setField(String name, Value value) { fields.put(name, Objects.requireNonNull(value)); }
    /** Fields in declaration order, so the inspector reads the way the source does. */
    public Map<String, Value> fields() {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public Value invoke(Interpreter interpreter, String methodName, Value... arguments) {
        return interpreter.invoke(this, methodName, arguments);
    }

    @Override public String toString() { return className + fields; }
}
