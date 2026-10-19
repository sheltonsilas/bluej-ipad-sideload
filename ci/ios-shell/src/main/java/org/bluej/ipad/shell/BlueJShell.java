package org.bluej.ipad.shell;

import java.util.Base64;
import java.util.Map;
import org.bluej.ipad.spike.InterpretedArray;
import org.bluej.ipad.spike.InterpretedObject;
import org.bluej.ipad.spike.InterpretedThrowable;
import org.bluej.ipad.spike.Interpreter;
import org.bluej.ipad.spike.Value;

/**
 * Minimal non-JavaFX iOS entry point for the BlueJ-for-iPad feasibility spike.
 *
 * It embeds the bytecode of {@code bluej.example.Counter} (see
 * {@code ci/ios-shell/example}) and executes it through the interpreted runtime, so a
 * native iOS build can be produced before any UI exists. The self test prints one line
 * per check and a final {@code SELFTEST RESULT} line, and exits non-zero on failure so
 * CI and the simulator/device logs carry the outcome.
 *
 * Regenerate the embedded bytecode with {@code ci/ios-shell/tools/embed-counter.sh}.
 */
public final class BlueJShell {

    private static final String CLASS_NAME = "bluej.example.Counter";

    /** Base64 of bluej/example/Counter.class compiled with --release 21. */
    private static final String COUNTER_CLASS_BASE64 = "yv66vgAAAEEATwoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCQAIAAkHAAoMAAsADAEAFWJsdWVqL2V4YW1wbGUvQ291bnRlcgEABWNvdW50AQABSQkACAAODAAPAAwBAAdjcmVhdGVkCAARAQAKaGVsbG8gaVBhZAZAAAAAAAAAABIAAAAVDAAWABcBABdtYWtlQ29uY2F0V2l0aENvbnN0YW50cwEAJyhMamF2YS9sYW5nL1N0cmluZztJKUxqYXZhL2xhbmcvU3RyaW5nOwoAGQAaBwAbDAAcAB0BABBqYXZhL2xhbmcvU3RyaW5nAQAGY2hhckF0AQAEKEkpQxIAAQAfDAAWACABABYoQ0MpTGphdmEvbGFuZy9TdHJpbmc7BwAiAQAdamF2YS9sYW5nL0FyaXRobWV0aWNFeGNlcHRpb24BAAQoSSlWAQAEQ29kZQEAD0xpbmVOdW1iZXJUYWJsZQEACWluY3JlbWVudAEABChJKUkBAAdkb3VibGVkAQADKClJAQAKYXJpdGhtZXRpYwEACGdyZWV0aW5nAQAUKClMamF2YS9sYW5nL1N0cmluZzsBAAVzdW1UbwEADVN0YWNrTWFwVGFibGUBAAlmaWJvbmFjY2kBAAdhdmVyYWdlAQAFKElJKUQBAAhkZXNjcmliZQEACGluaXRpYWxzAQA4KExqYXZhL2xhbmcvU3RyaW5nO0xqYXZhL2xhbmcvU3RyaW5nOylMamF2YS9sYW5nL1N0cmluZzsBAAV0b3RhbAEABShbSSlJBwA4AQACW0kBAApzYWZlRGl2aWRlAQAFKElJKUkBAAVjcmFzaAEAClNvdXJjZUZpbGUBAAxDb3VudGVyLmphdmEBABBCb290c3RyYXBNZXRob2RzCABAAQAQASBpcyABIHllYXJzIG9sZAgAQgEAAgEBDwYARAoARQBGBwBHDAAWAEgBACRqYXZhL2xhbmcvaW52b2tlL1N0cmluZ0NvbmNhdEZhY3RvcnkBAJgoTGphdmEvbGFuZy9pbnZva2UvTWV0aG9kSGFuZGxlcyRMb29rdXA7TGphdmEvbGFuZy9TdHJpbmc7TGphdmEvbGFuZy9pbnZva2UvTWV0aG9kVHlwZTtMamF2YS9sYW5nL1N0cmluZztbTGphdmEvbGFuZy9PYmplY3Q7KUxqYXZhL2xhbmcvaW52b2tlL0NhbGxTaXRlOwEADElubmVyQ2xhc3NlcwcASwEAJWphdmEvbGFuZy9pbnZva2UvTWV0aG9kSGFuZGxlcyRMb29rdXAHAE0BAB5qYXZhL2xhbmcvaW52b2tlL01ldGhvZEhhbmRsZXMBAAZMb29rdXAAIQAIAAIAAAACAAoADwAMAAAAAgALAAwAAAAOAAEABQAjAAEAJAAAADYAAgACAAAAEiq3AAEqG7UAB7IADQRgswANsQAAAAEAJQAAABIABAAAABMABAAUAAkAFQARABYAAQAmACcAAQAkAAAAKwADAAIAAAAPKiq0AAcbYLUAByq0AAesAAAAAQAlAAAACgACAAAAGQAKABoAAQAoACkAAQAkAAAAHwACAAEAAAAHKrQABwVorAAAAAEAJQAAAAYAAQAAAB4ACQAqACcAAQAkAAAAHgACAAEAAAAGGgZgBWisAAAAAQAlAAAABgABAAAAIgAJACsALAABACQAAAAbAAEAAAAAAAMSELAAAAABACUAAAAGAAEAAAAmAAkALQAnAAEAJAAAAGYAAwADAAAAJAM8BD0cGqMAHBwFcJoAChscYDynAAkbHAVoYDyEAgGn/+UbrAAAAAIAJQAAAB4ABwAAACoAAgArAAkALAAPAC0AFgAvABwAKwAiADIALgAAAAwABP0ABAEBEQX6AAUACQAvACcAAQAkAAAAXgACAAQAAAAYAzwEPRqeABEbHGA+HDwdPYQA/6f/8RusAAAAAgAlAAAAJgAJAAAANgACADcABAA4AAgAOQAMADoADgA7ABAAPAATAD0AFgA+AC4AAAAIAAL9AAQBAREACQAwADEAAQAkAAAAIQAEAAIAAAAJGhtghxQAEm+vAAAAAQAlAAAABgABAAAAQgAJADIAFwABACQAAAAgAAIAAgAAAAgqG7oAFAAAsAAAAAEAJQAAAAYAAQAAAEYACQAzADQAAQAkAAAAKAADAAIAAAAQKgO2ABgrA7YAGLoAHgAAsAAAAAEAJQAAAAYAAQAAAEoACQA1ADYAAQAkAAAAZgACAAYAAAAjAzwqTSy+PgM2BBUEHaIAFCwVBC42BRsVBWA8hAQBp//sG6wAAAACACUAAAAWAAUAAABOAAIATwAWAFAAGwBPACEAUgAuAAAAFQAC/wAKAAUHADcBBwA3AQEAAPgAFgAJADkAOgABACQAAAA7AAIAAwAAAAcaG2ysTQKsAAEAAAADAAQAIQACACUAAAAOAAMAAABXAAQAWAAFAFkALgAAAAYAAUQHACEACQA7ADYAAQAkAAAAHQACAAEAAAAFKhAHLqwAAAABACUAAAAGAAEAAABeAAkADwApAAEAJAAAABwAAQAAAAAABLIADawAAAABACUAAAAGAAEAAABiAAMAPAAAAAIAPQA+AAAADgACAEMAAQA/AEMAAQBBAEkAAAAKAAEASgBMAE4AGQ==";

    private static int failed;
    private static int passed;

    public static void main(String[] args) {
        System.out.println("BlueJ iPad runtime shell (feasibility spike)");
        System.out.println("vm=" + System.getProperty("java.vm.name")
                + " java=" + System.getProperty("java.version")
                + " os=" + System.getProperty("os.name") + "/" + System.getProperty("os.arch"));

        byte[] classBytes = Base64.getDecoder().decode(COUNTER_CLASS_BASE64);
        System.out.println("embedded class bytes=" + classBytes.length);

        Interpreter interpreter = new Interpreter();
        interpreter.load(Map.of(CLASS_NAME, classBytes));

        check("static arithmetic(10) == 26",
                new Value.IntValue(26),
                interpreter.invokeStatic(CLASS_NAME, "arithmetic", new Value.IntValue(10)));
        check("static greeting() == \"hello iPad\"",
                new Value.StringValue("hello iPad"),
                interpreter.invokeStatic(CLASS_NAME, "greeting"));
        check("sumTo(6) == 30 (loop, branch, modulo)",
                new Value.IntValue(30),
                interpreter.invokeStatic(CLASS_NAME, "sumTo", new Value.IntValue(6)));
        check("fibonacci(10) == 55 (while loop, locals)",
                new Value.IntValue(55),
                interpreter.invokeStatic(CLASS_NAME, "fibonacci", new Value.IntValue(10)));
        check("average(2, 3) == 2.5 (double arithmetic)",
                new Value.DoubleValue(2.5),
                interpreter.invokeStatic(CLASS_NAME, "average", new Value.IntValue(2), new Value.IntValue(3)));
        check("describe(\"Ada\", 36) (string concatenation)",
                new Value.StringValue("Ada is 36 years old"),
                interpreter.invokeStatic(CLASS_NAME, "describe", new Value.StringValue("Ada"), new Value.IntValue(36)));
        check("initials(\"Ada\", \"Lovelace\") == \"AL\" (char concatenation)",
                new Value.StringValue("AL"),
                interpreter.invokeStatic(CLASS_NAME, "initials", new Value.StringValue("Ada"), new Value.StringValue("Lovelace")));
        check("total({2, 3, 4}) == 9 (int array)",
                new Value.IntValue(9),
                interpreter.invokeStatic(CLASS_NAME, "total", new Value.ArrayValue(intArray(2, 3, 4))));
        check("safeDivide(1, 0) == -1 (caught ArithmeticException)",
                new Value.IntValue(-1),
                interpreter.invokeStatic(CLASS_NAME, "safeDivide", new Value.IntValue(1), new Value.IntValue(0)));

        InterpretedObject counter = interpreter.newObject(CLASS_NAME, new Value.IntValue(4));
        check("field count == 4", new Value.IntValue(4), counter.getField("count"));
        check("increment(5) == 9", new Value.IntValue(9),
                counter.invoke(interpreter, "increment", new Value.IntValue(5)));
        check("doubled() == 18", new Value.IntValue(18), counter.invoke(interpreter, "doubled"));
        check("field count == 9 after increment", new Value.IntValue(9), counter.getField("count"));
        check("static created() == 1 (static initialiser and field)",
                new Value.IntValue(1), interpreter.invokeStatic(CLASS_NAME, "created"));

        try {
            interpreter.invokeStatic(CLASS_NAME, "crash", new Value.ArrayValue(intArray(1)));
            check("uncaught exception is reported", new Value.StringValue("exception"),
                    new Value.StringValue("no exception"));
        } catch (InterpretedThrowable thrown) {
            check("uncaught exception is reported", new Value.StringValue("java.lang.ArrayIndexOutOfBoundsException"),
                    new Value.StringValue(thrown.className()));
        }

        System.out.println("SELFTEST RESULT: " + (failed == 0 ? "PASS" : "FAIL")
                + " (" + passed + " passed, " + failed + " failed)");
        if (failed != 0) {
            System.exit(1);
        }
    }

    private static InterpretedArray intArray(int... values) {
        InterpretedArray array = new InterpretedArray(11, "int", values.length);
        for (int i = 0; i < values.length; i++) {
            array.set(i, new Value.IntValue(values[i]));
        }
        return array;
    }

    private static void check(String name, Value expected, Value actual) {
        boolean ok = expected.equals(actual);
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        System.out.println((ok ? "PASS " : "FAIL ") + name + " -> " + describe(actual)
                + (ok ? "" : " (expected " + describe(expected) + ")"));
    }

    private static String describe(Value value) {
        if (value instanceof Value.IntValue intValue) {
            return Integer.toString(intValue.value());
        }
        if (value instanceof Value.DoubleValue doubleValue) {
            return Double.toString(doubleValue.value());
        }
        if (value instanceof Value.StringValue stringValue) {
            return "\"" + stringValue.value() + "\"";
        }
        return String.valueOf(value);
    }
}
