package org.bluej.ipad.spike;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InterpreterTest {
    private static final String COUNTER = """
            public class Counter {
                private int count;
                public Counter(int start) { this.count = start; }
                public int increment(int amount) { this.count = this.count + amount; return this.count; }
                public int doubled() { return this.count * 2; }
                public static int arithmetic(int x) { return (x + 3) * 2; }
                public static String greeting() { return "hello iPad"; }
            }
            """;

    private static final String CONTROL_FLOW = """
            public class ControlFlow {
                public static int sumTo(int limit) {
                    int total = 0;
                    for (int i = 1; i <= limit; i++) {
                        if (i % 2 == 0) { total += i; } else { total += i * 2; }
                    }
                    return total;
                }
                public static int factorial(int n) {
                    return n <= 1 ? 1 : n * factorial(n - 1);
                }
                public static int fibonacci(int n) {
                    int a = 0, b = 1;
                    while (n > 0) { int next = a + b; a = b; b = next; n--; }
                    return a;
                }
                public static String label(int n) {
                    return switch (n) { case 0 -> "zero"; case 1, 2 -> "small"; default -> "large"; };
                }
            }
            """;

    private static final String ARRAYS = """
            public class Arrays {
                private int[] values = new int[3];
                public void set(int index, int value) { values[index] = value; }
                public int get(int index) { return values[index]; }
                public int sum() {
                    int total = 0;
                    for (int value : values) { total += value; }
                    return total;
                }
                public int length() { return values.length; }
                public static String join(String[] names) {
                    String result = "";
                    for (int i = 0; i < names.length; i++) {
                        result = result + names[i];
                        if (i < names.length - 1) { result = result + ", "; }
                    }
                    return result;
                }
                public static int[] range(int n) {
                    int[] result = new int[n];
                    for (int i = 0; i < n; i++) { result[i] = i * i; }
                    return result;
                }
            }
            """;

    private static final String STATICS = """
            public class Statics {
                private static int instances;
                private static String name;
                static { name = "Statics"; instances = 100; }
                private int value;
                public Statics(int value) { this.value = value; instances++; }
                public int value() { return value; }
                public static int instances() { return instances; }
                public static String name() { return name; }
            }
            """;

    private static final String STRINGS = """
            public class Texts {
                public static String describe(String name, int age) {
                    return name + " is " + age + " years old";
                }
                public static String initials(String first, String last) {
                    return "" + first.charAt(0) + last.charAt(0);
                }
                public static int consonants(String text) {
                    int count = 0;
                    for (int i = 0; i < text.length(); i++) {
                        char c = Character.toLowerCase(text.charAt(i));
                        if (c != 'a' && c != 'e' && c != 'i' && c != 'o' && c != 'u' && c != ' ') { count++; }
                    }
                    return count;
                }
                public static boolean same(String a, String b) { return a.equals(b); }
            }
            """;

    private static final String EXCEPTIONS = """
            public class Failures {
                public static int divide(int a, int b) {
                    try { return a / b; } catch (ArithmeticException e) { return -1; }
                }
                public static int read(int[] values, int index) {
                    try { return values[index]; } catch (ArrayIndexOutOfBoundsException e) { return -2; }
                }
                public static String nullLength(String text) {
                    try { return "length " + text.length(); } catch (NullPointerException e) { return "null text"; }
                }
                static class Boom extends RuntimeException {
                    Boom(String message) { super(message); }
                }
                public static String custom() {
                    try { throw new Boom("boom"); } catch (RuntimeException e) { return "caught " + e.getMessage(); }
                }
                public static int noHandler(int[] values) { return values[5]; }
            }
            """;

    private static final String DOUBLES = """
            public class Numbers {
                public static double average(int a, int b) { return (a + b) / 2.0; }
                public static double hypot(double a, double b) { return Math.sqrt(a * a + b * b); }
                public static boolean closeEnough(double a, double b) { return Math.abs(a - b) < 0.001; }
                public static double roundTrip(double value) { return value * 2 / 2; }
            }
            """;

    private static final String OUTPUT = """
            public class Printer {
                public static void run() {
                    System.out.println("hello");
                    System.out.print("count=");
                    System.out.println(3);
                }
            }
            """;

    private Interpreter compileAndLoad(String className, String source) {
        var compilation = new InMemoryCompiler().compile(className, source);
        assertTrue(compilation.succeeded(), compilation.diagnostics());
        Interpreter interpreter = new Interpreter();
        interpreter.load(compilation.classes());
        return interpreter;
    }

    @Test
    void executesArithmeticAndStringConstants() {
        Interpreter interpreter = compileAndLoad("Counter", COUNTER);
        assertEquals(new Value.IntValue(26), interpreter.invokeStatic("Counter", "arithmetic", new Value.IntValue(10)));
        assertEquals(new Value.StringValue("hello iPad"), interpreter.invokeStatic("Counter", "greeting"));
    }

    @Test
    void constructsAndCallsMethodsOnInterpretedObject() {
        Interpreter interpreter = compileAndLoad("Counter", COUNTER);
        var object = interpreter.newObject("Counter", new Value.IntValue(4));
        assertEquals(new Value.IntValue(4), object.getField("count"));
        assertEquals(new Value.IntValue(9), object.invoke(interpreter, "increment", new Value.IntValue(5)));
        assertEquals(new Value.IntValue(18), object.invoke(interpreter, "doubled"));
        assertEquals(new Value.IntValue(9), object.getField("count"));
    }

    @Test
    void executesLoopsBranchesSwitchesAndRecursion() {
        Interpreter interpreter = compileAndLoad("ControlFlow", CONTROL_FLOW);
        // 1*2 + 2 + 3*2 + 4 + 5*2 + 6 = 30 for limit 6.
        assertEquals(new Value.IntValue(30), interpreter.invokeStatic("ControlFlow", "sumTo", new Value.IntValue(6)));
        assertEquals(new Value.IntValue(120), interpreter.invokeStatic("ControlFlow", "factorial", new Value.IntValue(5)));
        assertEquals(new Value.IntValue(55), interpreter.invokeStatic("ControlFlow", "fibonacci", new Value.IntValue(10)));
        assertEquals(new Value.StringValue("zero"), interpreter.invokeStatic("ControlFlow", "label", new Value.IntValue(0)));
        assertEquals(new Value.StringValue("small"), interpreter.invokeStatic("ControlFlow", "label", new Value.IntValue(2)));
        assertEquals(new Value.StringValue("large"), interpreter.invokeStatic("ControlFlow", "label", new Value.IntValue(7)));
    }

    @Test
    void executesArraysIncludingObjectArraysAndReturnedArrays() {
        Interpreter interpreter = compileAndLoad("Arrays", ARRAYS);
        var holder = interpreter.newObject("Arrays");
        holder.invoke(interpreter, "set", new Value.IntValue(0), new Value.IntValue(4));
        holder.invoke(interpreter, "set", new Value.IntValue(1), new Value.IntValue(5));
        assertEquals(new Value.IntValue(9), holder.invoke(interpreter, "sum"));
        assertEquals(new Value.IntValue(3), holder.invoke(interpreter, "length"));
        assertEquals(new Value.StringValue("a, b"), interpreter.invokeStatic("Arrays", "join",
                new Value.ArrayValue(stringArray("a", "b"))));
        Value returned = interpreter.invokeStatic("Arrays", "range", new Value.IntValue(4));
        assertInstanceOf(Value.ArrayValue.class, returned);
        InterpretedArray array = ((Value.ArrayValue) returned).value();
        assertEquals(4, array.length());
        assertEquals(new Value.IntValue(9), array.get(3));
    }

    @Test
    void runsStaticInitialisersOnceAndTracksStaticFields() {
        Interpreter interpreter = compileAndLoad("Statics", STATICS);
        assertEquals(new Value.IntValue(100), interpreter.invokeStatic("Statics", "instances"));
        assertEquals(new Value.StringValue("Statics"), interpreter.invokeStatic("Statics", "name"));
        interpreter.newObject("Statics", new Value.IntValue(7));
        interpreter.newObject("Statics", new Value.IntValue(8));
        assertEquals(new Value.IntValue(102), interpreter.invokeStatic("Statics", "instances"));
    }

    @Test
    void concatenatesStringsAndCallsStringIntrinsics() {
        Interpreter interpreter = compileAndLoad("Texts", STRINGS);
        assertEquals(new Value.StringValue("Ada is 36 years old"),
                interpreter.invokeStatic("Texts", "describe", new Value.StringValue("Ada"), new Value.IntValue(36)));
        assertEquals(new Value.StringValue("AL"),
                interpreter.invokeStatic("Texts", "initials", new Value.StringValue("Ada"), new Value.StringValue("Lovelace")));
        assertEquals(new Value.IntValue(2),
                interpreter.invokeStatic("Texts", "consonants", new Value.StringValue("abc")));
        assertEquals(new Value.IntValue(1),
                interpreter.invokeStatic("Texts", "same", new Value.StringValue("x"), new Value.StringValue("x")));
    }

    @Test
    void handlesTryCatchForRuntimeAndUserExceptions() {
        Interpreter interpreter = compileAndLoad("Failures", EXCEPTIONS);
        assertEquals(new Value.IntValue(-1), interpreter.invokeStatic("Failures", "divide",
                new Value.IntValue(1), new Value.IntValue(0)));
        assertEquals(new Value.IntValue(7), interpreter.invokeStatic("Failures", "divide",
                new Value.IntValue(21), new Value.IntValue(3)));
        assertEquals(new Value.IntValue(-2), interpreter.invokeStatic("Failures", "read",
                new Value.ArrayValue(new InterpretedArray(11, "int", 2)), new Value.IntValue(5)));
        assertEquals(new Value.StringValue("null text"), interpreter.invokeStatic("Failures", "nullLength", Value.NullValue.INSTANCE));
        assertEquals(new Value.StringValue("caught boom"), interpreter.invokeStatic("Failures", "custom"));

        InterpretedThrowable uncaught = assertThrows(InterpretedThrowable.class,
                () -> interpreter.invokeStatic("Failures", "noHandler", new Value.ArrayValue(new InterpretedArray(11, "int", 2))));
        assertEquals("java.lang.ArrayIndexOutOfBoundsException", uncaught.className());
    }

    @Test
    void computesWithDoublesAndMathIntrinsics() {
        Interpreter interpreter = compileAndLoad("Numbers", DOUBLES);
        assertEquals(new Value.DoubleValue(2.5), interpreter.invokeStatic("Numbers", "average",
                new Value.IntValue(2), new Value.IntValue(3)));
        assertEquals(new Value.DoubleValue(5.0), interpreter.invokeStatic("Numbers", "hypot",
                new Value.DoubleValue(3), new Value.DoubleValue(4)));
        assertEquals(new Value.DoubleValue(0.4), interpreter.invokeStatic("Numbers", "roundTrip", new Value.DoubleValue(0.4)));
    }

    @Test
    void capturesPrintStreamOutput() {
        Interpreter interpreter = compileAndLoad("Printer", OUTPUT);
        interpreter.invokeStatic("Printer", "run");
        assertEquals("hello\ncount=3\n", interpreter.output());
        interpreter.clearOutput();
        assertEquals("", interpreter.output());
    }

    @Test
    void stopsRunawayProgramsAtTheInstructionBudget() {
        var compilation = new InMemoryCompiler().compile("Loop", """
                public class Loop {
                    public static int run(int x) { while (x > 0) { x++; } return x; }
                }
                """);
        assertTrue(compilation.succeeded(), compilation.diagnostics());
        Interpreter bounded = new Interpreter(5_000);
        bounded.load(compilation.classes());
        assertThrows(IllegalStateException.class, () -> bounded.invokeStatic("Loop", "run", new Value.IntValue(1)));
    }

    @Test
    void refusesUnsupportedBytecodeExplicitly() {
        Interpreter interpreter = compileAndLoad("Wide", """
                public class Wide {
                    public static long run(long x) { return x + 1; }
                }
                """);
        assertThrows(UnsupportedOperationException.class,
                () -> interpreter.invokeStatic("Wide", "run", new Value.IntValue(1)));
    }

    private static InterpretedArray stringArray(String... values) {
        InterpretedArray array = new InterpretedArray(0, "java.lang.String", values.length);
        for (int i = 0; i < values.length; i++) array.set(i, new Value.StringValue(values[i]));
        return array;
    }
}

