package org.bluej.ipad.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.bluej.ipad.spike.InMemoryCompiler;
import org.bluej.ipad.spike.Value;
import org.junit.jupiter.api.Test;

class CoreSessionTest {
    private static final String COUNTER = """
            public class Counter {
                private static int created;
                private int count;
                public Counter(int start) { this.count = start; created++; }
                public int increment(int amount) { this.count += amount; return this.count; }
                public static int arithmetic(int x) { return (x + 3) * 2; }
                public static String greeting() { return "hello iPad"; }
                public static int created() { return created; }
            }
            """;

    private static final String PRINTER = """
            public class Printer {
                public static void run() { System.out.println("bench says hi"); }
            }
            """;

    private static final String READING = """
            import java.util.Scanner;

            public class Reading {
                public static int twice() {
                    Scanner in = new Scanner(System.in);
                    System.out.print("Number: ");
                    int value = in.nextInt() * 2;
                    System.out.println(value);
                    return value;
                }
                public static String join() {
                    Scanner in = new Scanner(System.in);
                    return in.nextLine() + "+" + in.nextLine();
                }
                public static String promptThenLine() {
                    Scanner in = new Scanner(System.in);
                    System.out.print("Name: ");
                    String name = in.nextLine().trim();
                    return "Hello " + name;
                }
            }
            """;

    private static CoreSession session() {
        CoreSession session = new CoreSession();
        load(session, "Counter", COUNTER);
        load(session, "Printer", PRINTER);
        return session;
    }

    private static void load(CoreSession session, String name, String source) {
        var compilation = new InMemoryCompiler().compile(name, source);
        assertTrue(compilation.succeeded(), compilation.diagnostics());
        session.load(compilation.classes());
    }

    @Test
    void listsClassesWithFieldsAndMethods() {
        List<ClassInfo> classes = session().classes();
        ClassInfo counter = classes.stream().filter(c -> c.name().equals("Counter")).findFirst().orElseThrow();
        assertTrue(counter.fields().stream().anyMatch(f -> f.name().equals("count") && f.type().equals("int") && !f.isStatic()));
        assertTrue(counter.fields().stream().anyMatch(f -> f.name().equals("created") && f.isStatic()));
        ClassInfo.Method increment = counter.methods().stream()
                .filter(m -> m.name().equals("increment")).findFirst().orElseThrow();
        assertEquals(List.of("int"), increment.parameterTypes());
        assertEquals("int", increment.returnType());
        assertFalse(increment.isStatic());
        assertTrue(counter.methods().stream().anyMatch(m -> m.name().equals("greeting") && m.returnType().equals("java.lang.String")));
    }

    @Test
    void putsObjectsOnTheBenchAndKeepsTheirState() {
        CoreSession session = session();
        BenchObject created = session.create("Counter", List.of(new Value.IntValue(4)));
        assertEquals(1, created.handle());
        assertEquals("Counter", created.className());
        assertEquals(new Value.IntValue(4), created.fields().get("count"));

        assertEquals(new Value.IntValue(9),
                session.invoke(created.handle(), "increment", List.of(new Value.IntValue(5))).value());
        assertEquals(new Value.IntValue(9), session.inspect(created.handle()).fields().get("count"));
        assertEquals(1, session.bench().size());
        assertEquals(new Value.IntValue(1), session.invokeStatic("Counter", "created", List.of()).value());
    }

    @Test
    void reportsUnloadedClassesAndHandlesClearly() {
        CoreSession session = session();
        assertThrows(IllegalArgumentException.class, () -> session.create("Missing", List.of()));
        assertThrows(IllegalArgumentException.class, () -> session.invoke(99, "increment", List.of(new Value.IntValue(1))));
        assertThrows(IllegalArgumentException.class, () -> session.reference(99));
    }

    @Test
    void aCallThatReadsInputWaitsForTheTerminal() {
        CoreSession session = session();
        load(session, "Reading", READING);

        CoreSession.CallState waiting = session.invokeStatic("Reading", "twice", List.of());
        assertTrue(waiting.needsInput());
        assertNull(waiting.value());
        assertEquals("Number: ", session.output(), "output printed before the read is visible");

        // Changing the project under the parked call is refused, not raced.
        assertThrows(IllegalStateException.class, session::compile);
        assertThrows(IllegalStateException.class, () -> session.create("Counter", List.of()));

        CoreSession.CallState done = session.supplyInput("21");
        assertFalse(done.needsInput());
        assertEquals(new Value.IntValue(42), done.value());
        assertEquals("Number: 42\n", session.output());
    }

    @Test
    void eachReadParksAgainUntilTheProgramFinishes() {
        CoreSession session = session();
        load(session, "Reading", READING);

        CoreSession.CallState first = session.invokeStatic("Reading", "join", List.of());
        assertTrue(first.needsInput());
        CoreSession.CallState second = session.supplyInput("Ada");
        assertTrue(second.needsInput());
        CoreSession.CallState done = session.supplyInput("Lovelace");
        assertEquals(new Value.StringValue("Ada+Lovelace"), done.value());
    }

    @Test
    void cancellingAWaitingCallMakesTheSessionUsableAgain() {
        CoreSession session = session();
        load(session, "Reading", READING);

        assertTrue(session.invokeStatic("Reading", "twice", List.of()).needsInput());
        session.cancelCall();

        assertThrows(IllegalArgumentException.class, () -> session.supplyInput("5"));
        assertEquals(new Value.IntValue(6),
                session.invokeStatic("Counter", "arithmetic", List.of(new Value.IntValue(0))).value());
        assertTrue(session.compile().success(), "the paused program no longer blocks compilation");
    }

    @Test
    void supplyingInputWithNoWaitingProgramIsRejected() {
        CoreSession session = session();
        assertThrows(IllegalArgumentException.class, () -> session.supplyInput("5"));
    }

    @Test
    void inputDuringADebugRunFailsInsteadOfHanging() {
        CoreSession session = new CoreSession();
        load(session, "Reading", READING);
        session.debugStart("Reading", "promptThenLine", List.of());
        assertTrue(session.debugging());
        DebugSession.Snapshot snapshot = session.debugCommand("continue");
        assertEquals("failed", snapshot.status());
        assertTrue(snapshot.errorMessage().contains("input"), snapshot.errorMessage());
    }

    @Test
    void capturesAndClearsConsoleOutput() {
        CoreSession session = session();
        session.invokeStatic("Printer", "run", List.of());
        assertEquals("bench says hi\n", session.output());
        session.clearOutput();
        assertEquals("", session.output());
    }
}
