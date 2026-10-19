package org.bluej.ipad.spike;

import java.util.Objects;

/**
 * A value on the interpreter's operand stack, in a local variable or in a field.
 *
 * <p>Booleans, bytes, chars and shorts are represented as {@link IntValue}; there is no
 * automatic boxing yet. {@code long} and {@code float} are deliberately absent: the
 * interpreter rejects their bytecodes instead of silently computing the wrong result.
 */
public sealed interface Value
        permits Value.IntValue, Value.DoubleValue, Value.StringValue,
                Value.ObjectValue, Value.ArrayValue, Value.NullValue {

    record IntValue(int value) implements Value {}

    record DoubleValue(double value) implements Value {}

    record StringValue(String value) implements Value {
        public StringValue { Objects.requireNonNull(value); }
    }

    record ObjectValue(InterpretedObject value) implements Value {
        public ObjectValue { Objects.requireNonNull(value); }
    }

    record ArrayValue(InterpretedArray value) implements Value {
        public ArrayValue { Objects.requireNonNull(value); }
    }

    enum NullValue implements Value { INSTANCE }
}
