package org.bluej.ipad.shell;

import java.util.Base64;
import java.util.Map;
import org.bluej.ipad.spike.InterpretedObject;
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

    /** Base64 of bluej/example/Counter.class compiled with --release 21. */
    private static final String COUNTER_CLASS_BASE64 = "yv66vgAAAEEAGwoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCQAIAAkHAAoMAAsADAEAFWJsdWVqL2V4YW1wbGUvQ291bnRlcgEABWNvdW50AQABSQgADgEACmhlbGxvIGlQYWQBAAQoSSlWAQAEQ29kZQEAD0xpbmVOdW1iZXJUYWJsZQEACWluY3JlbWVudAEABChJKUkBAAdkb3VibGVkAQADKClJAQAKYXJpdGhtZXRpYwEACGdyZWV0aW5nAQAUKClMamF2YS9sYW5nL1N0cmluZzsBAApTb3VyY2VGaWxlAQAMQ291bnRlci5qYXZhACEACAACAAAAAQACAAsADAAAAAUAAQAFAA8AAQAQAAAAKgACAAIAAAAKKrcAASobtQAHsQAAAAEAEQAAAA4AAwAAAA4ABAAPAAkAEAABABIAEwABABAAAAArAAMAAgAAAA8qKrQABxtgtQAHKrQAB6wAAAABABEAAAAKAAIAAAATAAoAFAABABQAFQABABAAAAAfAAIAAQAAAAcqtAAHBWisAAAAAQARAAAABgABAAAAGAAJABYAEwABABAAAAAeAAIAAQAAAAYaBmAFaKwAAAABABEAAAAGAAEAAAAcAAkAFwAYAAEAEAAAABsAAQAAAAAAAxINsAAAAAEAEQAAAAYAAQAAACAAAQAZAAAAAgAa";

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
        interpreter.load(Map.of("bluej.example.Counter", classBytes));

        check("static arithmetic(10) == 26",
                new Value.IntValue(26),
                interpreter.invokeStatic("bluej.example.Counter", "arithmetic", new Value.IntValue(10)));
        check("static greeting() == \"hello iPad\"",
                new Value.StringValue("hello iPad"),
                interpreter.invokeStatic("bluej.example.Counter", "greeting"));

        InterpretedObject counter = interpreter.newObject("bluej.example.Counter", new Value.IntValue(4));
        check("field count == 4", new Value.IntValue(4), counter.getField("count"));
        check("increment(5) == 9", new Value.IntValue(9),
                counter.invoke(interpreter, "increment", new Value.IntValue(5)));
        check("doubled() == 18", new Value.IntValue(18), counter.invoke(interpreter, "doubled"));
        check("field count == 9 after increment", new Value.IntValue(9), counter.getField("count"));

        System.out.println("SELFTEST RESULT: " + (failed == 0 ? "PASS" : "FAIL")
                + " (" + passed + " passed, " + failed + " failed)");
        if (failed != 0) {
            System.exit(1);
        }
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
        if (value instanceof Value.StringValue stringValue) {
            return "\"" + stringValue.value() + "\"";
        }
        return String.valueOf(value);
    }
}
