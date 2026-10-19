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

        assertEquals(new Value.IntValue(9), session.invoke(created.handle(), "increment", List.of(new Value.IntValue(5))));
        assertEquals(new Value.IntValue(9), session.inspect(created.handle()).fields().get("count"));
        assertEquals(1, session.bench().size());
        assertEquals(new Value.IntValue(1), session.invokeStatic("Counter", "created", List.of()));
    }

    @Test
    void reportsUnloadedClassesAndHandlesClearly() {
        CoreSession session = session();
        assertThrows(IllegalArgumentException.class, () -> session.create("Missing", List.of()));
        assertThrows(IllegalArgumentException.class, () -> session.invoke(99, "increment", List.of(new Value.IntValue(1))));
        assertThrows(IllegalArgumentException.class, () -> session.reference(99));
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
