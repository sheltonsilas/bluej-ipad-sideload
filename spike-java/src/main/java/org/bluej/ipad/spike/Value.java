package org.bluej.ipad.spike;

import java.util.Objects;

public sealed interface Value permits Value.IntValue, Value.StringValue, Value.ObjectValue, Value.NullValue {
    record IntValue(int value) implements Value {}
    record StringValue(String value) implements Value {
        public StringValue { Objects.requireNonNull(value); }
    }
    record ObjectValue(InterpretedObject value) implements Value {
        public ObjectValue { Objects.requireNonNull(value); }
    }
    enum NullValue implements Value { INSTANCE }
}
