package org.bluej.ipad.spike;

import java.util.Arrays;
import java.util.Objects;

/**
 * An array created and used entirely inside the interpreter.
 *
 * <p>Element kind is derived from the JVM type code ({@code newarray}) or the component
 * class name ({@code anewarray}). Elements are stored as {@link Value} so the inspector can
 * show them without converting to host Java objects.
 */
public final class InterpretedArray {
    /** JVM {@code newarray} type code, or {@code 0} for a reference array. */
    private final int primitiveType;
    private final String componentType;
    private final Value[] elements;

    /**
     * @param primitiveType JVM {@code newarray} type code (11 = int) or {@code 0} for a
     *                      reference array
     * @param componentType component type shown by the inspector, e.g. {@code int}
     */
    public InterpretedArray(int primitiveType, String componentType, int length) {
        if (length < 0) throw new IllegalArgumentException("negative array length: " + length);
        this.primitiveType = primitiveType;
        this.componentType = Objects.requireNonNull(componentType);
        this.elements = new Value[length];
        Value initial = switch (primitiveType) {
            case 5 -> new Value.DoubleValue(0.0);   // T_FLOAT, modelled as double
            case 6, 7 -> null;                      // T_DOUBLE, T_LONG unsupported below
            default -> new Value.IntValue(0);
        };
        if (primitiveType == 0) initial = Value.NullValue.INSTANCE;
        if (initial != null) Arrays.fill(elements, initial);
    }

    public int length() { return elements.length; }

    public String componentType() { return componentType; }

    public Value get(int index) {
        if (index < 0 || index >= elements.length) {
            throw new InterpretedThrowable("java.lang.ArrayIndexOutOfBoundsException",
                    "Index " + index + " out of bounds for length " + elements.length);
        }
        Value value = elements[index];
        return value == null ? Value.NullValue.INSTANCE : value;
    }

    public void set(int index, Value value) {
        if (index < 0 || index >= elements.length) {
            throw new InterpretedThrowable("java.lang.ArrayIndexOutOfBoundsException",
                    "Index " + index + " out of bounds for length " + elements.length);
        }
        elements[index] = Objects.requireNonNull(value);
    }

    /** Snapshot for the inspector; never exposes the mutable backing array. */
    public Value[] snapshot() { return elements.clone(); }

    @Override public String toString() {
        return componentType + "[" + elements.length + "]";
    }
}
