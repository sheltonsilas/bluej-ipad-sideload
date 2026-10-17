package org.bluej.ipad.spike;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InterpreterTest {
    private static final String SOURCE = """
            public class Counter {
                private int count;
                public Counter(int start) { this.count = start; }
                public int increment(int amount) { this.count = this.count + amount; return this.count; }
                public int doubled() { return this.count * 2; }
                public static int arithmetic(int x) { return (x + 3) * 2; }
                public static String greeting() { return \"hello iPad\"; }
            }
            """;

    private Interpreter compileAndLoad() {
        var compilation = new InMemoryCompiler().compile("Counter", SOURCE);
        assertTrue(compilation.succeeded(), compilation.diagnostics());
        var interpreter = new Interpreter();
        interpreter.load(compilation.classes());
        return interpreter;
    }

    @Test
    void executesArithmeticAndStringConstants() {
        var interpreter = compileAndLoad();
        assertEquals(new Value.IntValue(26), interpreter.invokeStatic("Counter", "arithmetic", new Value.IntValue(10)));
        assertEquals(new Value.StringValue("hello iPad"), interpreter.invokeStatic("Counter", "greeting"));
    }

    @Test
    void constructsAndCallsMethodsOnInterpretedObject() {
        var interpreter = compileAndLoad();
        var object = interpreter.newObject("Counter", new Value.IntValue(4));
        assertEquals(new Value.IntValue(4), object.getField("count"));
        assertEquals(new Value.IntValue(9), object.invoke(interpreter, "increment", new Value.IntValue(5)));
        assertEquals(new Value.IntValue(18), object.invoke(interpreter, "doubled"));
        assertEquals(new Value.IntValue(9), object.getField("count"));
    }

    @Test
    void refusesUnsupportedBytecodeExplicitly() {
        var compiler = new InMemoryCompiler();
        var result = compiler.compile("Loop", "public class Loop { public static int run(int x) { if (x > 0) return 1; return 0; } }");
        assertTrue(result.succeeded(), result.diagnostics());
        var interpreter = new Interpreter();
        interpreter.load(result.classes());
        assertThrows(UnsupportedOperationException.class,
                () -> interpreter.invokeStatic("Loop", "run", new Value.IntValue(1)));
    }
}
