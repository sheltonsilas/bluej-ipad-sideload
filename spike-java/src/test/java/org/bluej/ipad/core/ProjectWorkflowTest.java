package org.bluej.ipad.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.bluej.ipad.spike.InterpretedThrowable;
import org.bluej.ipad.spike.Value;
import org.junit.jupiter.api.Test;

class ProjectWorkflowTest {

    private static final String COUNTER = """
            public class Counter {
                private int count;
                public Counter() { this.count = 0; }
                public void increment() { count++; }
                public int getCount() { return count; }
                public static String label() { return "count"; }
            }
            """;

    @Test
    void compilesASourceAndPublishesClassMetadata() {
        CoreSession session = new CoreSession();
        session.setSource("Counter.java", COUNTER);

        ClassInfo beforeCompile = session.classes().get(0);
        assertEquals("Counter", beforeCompile.name());
        assertFalse(beforeCompile.compiled());
        assertFalse(beforeCompile.error());
        assertEquals("Counter.java", beforeCompile.path());

        CoreSession.CompileReport report = session.compile();
        assertTrue(report.success(), () -> "diagnostics: " + report.diagnostics());
        ClassInfo afterCompile = session.classes().get(0);
        assertTrue(afterCompile.compiled());
        assertFalse(afterCompile.error());
        assertEquals("class", afterCompile.kind());
        assertEquals("java.lang.Object", afterCompile.superclass());
        assertEquals(List.of("count"), afterCompile.fields().stream().map(ClassInfo.Field::name).toList());
        assertTrue(afterCompile.methods().stream()
                .anyMatch(m -> m.name().equals("increment") && !m.isStatic()));
        assertTrue(afterCompile.methods().stream()
                .anyMatch(m -> m.name().equals("label") && m.isStatic()));
    }

    @Test
    void constructsOnTheBenchCallsMethodsAndInspects() {
        CoreSession session = new CoreSession();
        session.setSource("Counter.java", COUNTER);
        assertTrue(session.compile().success());

        BenchObject counter = session.create("Counter", List.of());
        assertEquals("counter1", counter.name());
        assertEquals(1, counter.handle());

        session.invoke(counter.handle(), "increment", List.of());
        session.invoke(counter.handle(), "increment", List.of());
        assertEquals(new Value.IntValue(2), session.inspect(counter.handle()).fields().get("count"));

        assertEquals(new Value.StringValue("count"), session.invokeStatic("Counter", "label", List.of()));

        BenchObject second = session.create("Counter", List.of());
        assertEquals("counter2", second.name());

        session.removeObject(counter.handle());
        assertEquals(List.of(second.handle()),
                session.bench().stream().map(BenchObject::handle).toList());
    }

    @Test
    void aBrokenEditMarksTheClassErroneousAndKeepsTheRunningProgramIntact() {
        CoreSession session = new CoreSession();
        session.setSource("Counter.java", COUNTER);
        assertTrue(session.compile().success());
        BenchObject counter = session.create("Counter", List.of());

        session.setSource("Counter.java", COUNTER.replace("count++;", "count++"));
        CoreSession.CompileReport broken = session.compile();

        assertFalse(broken.success());
        ClassInfo info = session.classes().get(0);
        assertTrue(info.error());
        assertFalse(info.compiled());
        assertTrue(broken.diagnostics().stream().anyMatch(d -> d.path().equals("Counter.java")));
        assertThrows(IllegalArgumentException.class, () -> session.create("Counter", List.of()));
        assertThrows(IllegalArgumentException.class, () -> session.invokeStatic("Counter", "label", List.of()));
        // The object that was already on the bench keeps working against the last good classes.
        session.invoke(counter.handle(), "increment", List.of());
        assertEquals(new Value.IntValue(1), session.inspect(counter.handle()).fields().get("count"));
    }

    @Test
    void aFailedCompilationKeepsTheLastGoodClasses() {
        CoreSession session = new CoreSession();
        session.setSource("Counter.java", COUNTER);
        assertTrue(session.compile().success());
        session.setSource("Broken.java", "public class Broken { void x( }");
        assertFalse(session.compile().success());

        // Counter is still runnable; Broken has a card but is not.
        assertTrue(session.classes().stream().anyMatch(c -> c.name().equals("Counter") && c.compiled()));
        assertTrue(session.classes().stream().anyMatch(c -> c.name().equals("Broken") && c.error()));
        session.create("Counter", List.of());
    }

    @Test
    void successfulRecompileResetsTheBenchLikeBlueJ() {
        CoreSession session = new CoreSession();
        session.setSource("Counter.java", COUNTER);
        session.compile();
        session.create("Counter", List.of());
        session.setSource("Counter.java", COUNTER.replace("\"count\"", "\"total\""));
        assertTrue(session.compile().success());
        assertTrue(session.bench().isEmpty(), "recompiling invalidates existing instances");
    }

    @Test
    void inheritanceWorksAcrossTheHierarchy() {
        CoreSession session = new CoreSession();
        session.setSource("Animal.java", """
                public class Animal {
                    private String name;
                    public Animal(String name) { this.name = name; }
                    public String getName() { return name; }
                    public String speak() { return "..."; }
                }
                """);
        session.setSource("Dog.java", """
                public class Dog extends Animal {
                    public Dog(String name) { super(name); }
                    @Override public String speak() { return getName() + " says woof"; }
                    public int legs() { return 4; }
                }
                """);
        CoreSession.CompileReport report = session.compile();
        assertTrue(report.success(), () -> "diagnostics: " + report.diagnostics());

        ClassInfo dog = session.classes().stream().filter(c -> c.name().equals("Dog")).findFirst().orElseThrow();
        assertEquals("Animal", dog.superclass());

        BenchObject rex = session.create("Dog", List.of(new Value.StringValue("Rex")));
        assertEquals(new Value.StringValue("Rex"), rex.fields().get("name"),
                "inherited fields exist on the instance");
        assertEquals(new Value.StringValue("Rex says woof"), session.invoke(rex.handle(), "speak", List.of()));
        assertEquals(new Value.StringValue("Rex"), session.invoke(rex.handle(), "getName", List.of()));
        assertEquals(new Value.IntValue(4), session.invoke(rex.handle(), "legs", List.of()));
    }

    @Test
    void longAndFloatProgramsRunThroughTheSession() {
        CoreSession session = new CoreSession();
        session.setSource("Wide.java", """
                public class Wide {
                    private long total;
                    public Wide(long start) { total = start; }
                    public static long twice(long value) { return value * 2; }
                    public static float ratio(long part, long whole) { return (float) part / whole; }
                    public long add(long amount) { total = total + amount; return total; }
                }
                """);
        assertTrue(session.compile().success());
        assertEquals(new Value.LongValue(4_000_000_000L),
                session.invokeStatic("Wide", "twice", List.of(new Value.LongValue(2_000_000_000L))));
        assertEquals(new Value.FloatValue(0.25f),
                session.invokeStatic("Wide", "ratio", List.of(new Value.LongValue(1), new Value.LongValue(4))));

        BenchObject wide = session.create("Wide", List.of(new Value.LongValue(5_000_000_000L)));
        assertEquals(new Value.LongValue(5_000_000_000L), wide.fields().get("total"));
        assertEquals(new Value.LongValue(6_000_000_000L),
                session.invoke(wide.handle(), "add", List.of(new Value.LongValue(1_000_000_000L))));
    }

    @Test
    void unsupportedBytecodeStaysExplicit() {
        CoreSession session = new CoreSession();
        session.setSource("Locked.java", """
                public class Locked { public static int run() { synchronized ("lock") { return 1; } } }
                """);
        assertTrue(session.compile().success());
        UnsupportedOperationException thrown = assertThrows(UnsupportedOperationException.class,
                () -> session.invokeStatic("Locked", "run", List.of()));
        assertTrue(thrown.getMessage().toLowerCase().contains("unsupported"), thrown.getMessage());
    }

    @Test
    void uncaughtStudentExceptionsKeepTheirRuntimeType() {
        CoreSession session = new CoreSession();
        session.setSource("Boom.java", """
                public class Boom { public static int divide(int a, int b) { return a / b; } }
                """);
        session.compile();
        InterpretedThrowable thrown = assertThrows(InterpretedThrowable.class,
                () -> session.invokeStatic("Boom", "divide", List.of(new Value.IntValue(1), new Value.IntValue(0))));
        assertEquals("java.lang.ArithmeticException", thrown.className());
    }
}
