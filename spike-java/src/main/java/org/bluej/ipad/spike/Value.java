package org.bluej.ipad.spike;

import java.util.Objects;

/**
 * A value on the interpreter's operand stack, in a local variable or in a field.
 *
 * <p>Booleans, bytes, chars and shorts are represented as {@link IntValue}. {@code float}
 * and {@code double} stay distinct rather than being widened to double, because Java prints
 * them with different precision ({@code Float.toString(1.1f)} is {@code "1.1"}, not
 * {@code "1.100000023841858"}), and a student's console output has to match BlueJ's.
 */
public sealed interface Value
        permits Value.IntValue, Value.LongValue, Value.FloatValue, Value.DoubleValue,
                Value.StringValue, Value.ObjectValue, Value.ArrayValue, Value.NullValue {

    record IntValue(int value) implements Value {}

    record LongValue(long value) implements Value {}

    record FloatValue(float value) implements Value {}

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
