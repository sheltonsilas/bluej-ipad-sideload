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

    private static final String WIDE_NUMBERS = """
            public class Wide {
                private long total;
                private float ratio;
                public Wide(long total, float ratio) { this.total = total; this.ratio = ratio; }
                public long total() { return total; }
                public void add(long amount) { total = total + amount; }
                public float ratio() { return ratio; }
                public static long factorial(int n) {
                    long result = 1;
                    for (int i = 2; i <= n; i++) { result = result * i; }
                    return result;
                }
                public static float half(float value) { return value / 2; }
                public static float scale(float value, int by) { return value * by; }
                public static double mix(int a, long b, float c) { return a + b + c; }
                public static long countdown(long n) {
                    while (n > 0) { n = n - 1; }
                    return n;
                }
                public static boolean isHuge(long value) { return value > 2000000000L; }
                public static int compare(long a, long b) { return a < b ? -1 : (a == b ? 0 : 1); }
                public static int narrow(long value) { return (int) value; }
                public static long widen(int value) { return value; }
                public static float toFloat(long value) { return value; }
                public static long truncate(double value) { return (long) value; }
                public static String describe(long big, float small) { return big + "|" + small; }
                public static long[] range(int n) {
                    long[] values = new long[n];
                    for (int i = 0; i < n; i++) { values[i] = i * 1000000000L; }
                    return values;
                }
                public static float[] halves(int n) {
                    float[] values = new float[n];
                    for (int i = 0; i < n; i++) { values[i] = i + 0.5f; }
                    return values;
                }
                public static String math(long big) {
                    return Math.abs(big) + " " + Math.round(2.6) + " " + Math.round(2.6f)
                            + " " + Math.max(big, 2L) + " " + Long.parseLong("9000000000");
                }
                public static void print() {
                    System.out.println(9000000000L);
                    System.out.println(1.5f);
                    System.out.println(0.5f + 0.25f);
                }
                public static long divide(long a, long b) { return a / b; }
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
    void computesWithLongsAndFloats() {
        Interpreter interpreter = compileAndLoad("Wide", WIDE_NUMBERS);
        assertEquals(new Value.LongValue(2432902008176640000L),
                interpreter.invokeStatic("Wide", "factorial", new Value.IntValue(20)));
        assertEquals(new Value.FloatValue(1.25f),
                interpreter.invokeStatic("Wide", "half", new Value.FloatValue(2.5f)));
        assertEquals(new Value.FloatValue(7.5f),
                interpreter.invokeStatic("Wide", "scale", new Value.FloatValue(2.5f), new Value.IntValue(3)));
        assertEquals(new Value.DoubleValue(8.5),
                interpreter.invokeStatic("Wide", "mix", new Value.IntValue(1),
                        new Value.LongValue(5), new Value.FloatValue(2.5f)));
        assertEquals(new Value.LongValue(0L),
                interpreter.invokeStatic("Wide", "countdown", new Value.LongValue(3)));
        assertEquals(new Value.IntValue(1),
                interpreter.invokeStatic("Wide", "isHuge", new Value.LongValue(3_000_000_000L)));
        assertEquals(new Value.IntValue(-1),
                interpreter.invokeStatic("Wide", "compare", new Value.LongValue(1), new Value.LongValue(2)));
        assertEquals(new Value.IntValue(0),
                interpreter.invokeStatic("Wide", "compare", new Value.LongValue(4), new Value.LongValue(4)));
        assertEquals(new Value.IntValue(42),
                interpreter.invokeStatic("Wide", "narrow", new Value.LongValue(42)));
        assertEquals(new Value.LongValue(42L),
                interpreter.invokeStatic("Wide", "widen", new Value.IntValue(42)));
        assertEquals(new Value.FloatValue(7f),
                interpreter.invokeStatic("Wide", "toFloat", new Value.LongValue(7)));
        assertEquals(new Value.LongValue(2L),
                interpreter.invokeStatic("Wide", "truncate", new Value.DoubleValue(2.9)));
        assertEquals(new Value.StringValue("5000000000|1.25"),
                interpreter.invokeStatic("Wide", "describe",
                        new Value.LongValue(5_000_000_000L), new Value.FloatValue(1.25f)));
        // Math.round(double) is a long, Math.max picks the long overload, Long.parseLong keeps 64 bits.
        assertEquals(new Value.StringValue("5 3 3 2 9000000000"),
                interpreter.invokeStatic("Wide", "math", new Value.LongValue(-5)));

        InterpretedThrowable thrown = assertThrows(InterpretedThrowable.class,
                () -> interpreter.invokeStatic("Wide", "divide", new Value.LongValue(1), new Value.LongValue(0)));
        assertEquals("java.lang.ArithmeticException", thrown.className());
    }

    @Test
    void longAndFloatArraysAndFieldsKeepTheirTypes() {
        Interpreter interpreter = compileAndLoad("Wide", WIDE_NUMBERS);

        InterpretedArray longs = ((Value.ArrayValue) interpreter.invokeStatic("Wide", "range",
                new Value.IntValue(3))).value();
        assertEquals("long", longs.componentType());
        assertEquals(new Value.LongValue(2_000_000_000L), longs.get(2));

        InterpretedArray floats = ((Value.ArrayValue) interpreter.invokeStatic("Wide", "halves",
                new Value.IntValue(2))).value();
        assertEquals("float", floats.componentType());
        assertEquals(new Value.FloatValue(0.5f), floats.get(0));
        assertEquals(new Value.FloatValue(1.5f), floats.get(1));

        var object = interpreter.newObject("Wide", new Value.LongValue(5_000_000_000L), new Value.FloatValue(0.5f));
        assertEquals(new Value.LongValue(5_000_000_000L), object.getField("total"));
        assertEquals(new Value.FloatValue(0.5f), object.getField("ratio"));
        object.invoke(interpreter, "add", new Value.LongValue(1_000_000_000L));
        assertEquals(new Value.LongValue(6_000_000_000L), object.getField("total"));
        assertEquals(new Value.LongValue(6_000_000_000L), object.invoke(interpreter, "total"));
    }

    @Test
    void printsLongsAndFloatsLikeJava() {
        Interpreter interpreter = compileAndLoad("Wide", WIDE_NUMBERS);
        interpreter.invokeStatic("Wide", "print");
        assertEquals("9000000000\n1.5\n0.75\n", interpreter.output());
    }

    @Test
    void refusesUnsupportedBytecodeExplicitly() {
        Interpreter interpreter = compileAndLoad("Locked", """
                public class Locked {
                    public static int run() { synchronized ("lock") { return 1; } }
                }
                """);
        assertThrows(UnsupportedOperationException.class, () -> interpreter.invokeStatic("Locked", "run"));
    }

    private static InterpretedArray stringArray(String... values) {
        InterpretedArray array = new InterpretedArray(0, "java.lang.String", values.length);
        for (int i = 0; i < values.length; i++) array.set(i, new Value.StringValue(values[i]));
        return array;
    }
}

