package org.bluej.ipad.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectProtocolTest {

    private static final String COUNTER = """
            public class Counter {
                private int count;
                public Counter(int start) { this.count = start; }
                public void increment() { count++; }
                public int getCount() { return count; }
            }
            """;

    private static Map<String, Object> call(CoreProtocol protocol, String request) {
        String response = protocol.handle(request);
        Object parsed = Json.parse(response);
        assertInstanceOf(Map.class, parsed, response);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) parsed;
        return map;
    }

    private static CoreProtocol protocol() {
        return new CoreProtocol(new CoreSession());
    }

    @Test
    void createsAProjectFileFromTheStandardTemplate() {
        CoreProtocol protocol = protocol();
        Map<String, Object> created = call(protocol, """
                {"op":"newClass","name":"Dog","kind":"class"}
                """);
        assertEquals("Dog.java", created.get("path"));
        String text = (String) created.get("text");
        assertTrue(text.contains("public class Dog"), text);
        assertTrue(text.contains("public Dog()"), text);
        assertEquals(1L, created.get("revision"));

        Map<String, Object> duplicate = call(protocol, """
                {"op":"newClass","name":"Dog","kind":"class"}
                """);
        assertTrue(((Map<?, ?>) duplicate.get("error")).get("message").toString().contains("already exists"));

        Map<String, Object> invalid = call(protocol, """
                {"op":"newClass","name":"9 lives","kind":"class"}
                """);
        assertTrue(((Map<?, ?>) invalid.get("error")).get("message").toString().contains("invalid"));
    }

    @Test
    void compilesAndReportsTheDiagramModel() {
        CoreProtocol protocol = protocol();
        call(protocol, """
                {"op":"newClass","name":"Counter","kind":"class"}
                """);
        call(protocol, """
                {"op":"setSource","path":"Counter.java","text":"%s"}
                """.formatted(COUNTER.replace("\n", "\\n").replace("\"", "\\\"")));

        Map<String, Object> sources = call(protocol, "{\"op\":\"sources\"}");
        assertEquals(1, ((List<?>) sources.get("sources")).size());

        Map<String, Object> compiled = call(protocol, "{\"op\":\"compile\"}");
        assertEquals(true, compiled.get("success"));
        assertEquals(List.of(), compiled.get("diagnostics"));
        List<?> classes = (List<?>) compiled.get("classes");
        Map<?, ?> counter = (Map<?, ?>) classes.get(0);
        assertEquals("Counter", counter.get("name"));
        assertEquals(true, counter.get("compiled"));
        assertEquals(false, counter.get("error"));
        assertEquals("Counter.java", counter.get("path"));
    }

    @Test
    void reportsCompileErrorsWithPositions() {
        CoreProtocol protocol = protocol();
        Map<String, Object> broken = call(protocol, """
                {"op":"setSource","path":"Counter.java","text":"public class Counter {\\n  int x = ;\\n}\\n"}
                """);
        assertEquals(true, broken.get("saved"));

        Map<String, Object> compiled = call(protocol, "{\"op\":\"compile\"}");
        assertEquals(false, compiled.get("success"));
        Map<?, ?> diagnostic = (Map<?, ?>) ((List<?>) compiled.get("diagnostics")).get(0);
        assertEquals("Counter.java", diagnostic.get("path"));
        assertEquals("error", diagnostic.get("severity"));
        assertEquals(2L, diagnostic.get("line"));
        Map<?, ?> card = (Map<?, ?>) ((List<?>) compiled.get("classes")).get(0);
        assertEquals(true, card.get("error"));
        assertEquals(false, card.get("compiled"));
    }

    @Test
    void runsTheBenchLifecycleOverJson() {
        CoreProtocol protocol = protocol();
        call(protocol, """
                {"op":"setSource","path":"Counter.java","text":"%s"}
                """.formatted(COUNTER.replace("\n", "\\n").replace("\"", "\\\"")));
        assertEquals(true, call(protocol, "{\"op\":\"compile\"}").get("success"));

        Map<String, Object> created = call(protocol, """
                {"op":"new","class":"Counter","args":[{"int":4}]}
                """);
        assertEquals(1L, created.get("handle"));
        assertEquals("counter1", created.get("name"));

        Map<String, Object> invoked = call(protocol, """
                {"op":"invoke","handle":1,"method":"getCount"}
                """);
        assertEquals(Map.of("int", 4L), invoked.get("value"));

        Map<String, Object> bench = call(protocol, "{\"op\":\"bench\"}");
        assertEquals("counter1", ((Map<?, ?>) ((List<?>) bench.get("bench")).get(0)).get("name"));

        Map<String, Object> removed = call(protocol, "{\"op\":\"removeObject\",\"handle\":1}");
        assertEquals(true, removed.get("removed"), removed.toString());
        Map<String, Object> afterRemoval = call(protocol, "{\"op\":\"bench\"}");
        assertEquals(0, ((List<?>) afterRemoval.get("bench")).size(), afterRemoval.toString());
    }

    @Test
    void deletingASourceRemovesItsClassCard() {
        CoreProtocol protocol = protocol();
        call(protocol, """
                {"op":"setSource","path":"Ghost.java","text":"public class Ghost { }"}
                """);
        assertTrue(call(protocol, "{\"op\":\"compile\"}").get("success").equals(true));
        assertEquals(1, ((List<?>) call(protocol, "{\"op\":\"classes\"}").get("classes")).size());

        assertEquals(true, call(protocol, "{\"op\":\"deleteSource\",\"path\":\"Ghost.java\"}").get("deleted"));
        Map<String, Object> compiled = call(protocol, "{\"op\":\"compile\"}");
        assertEquals(0, ((List<?>) compiled.get("classes")).size());
    }
}
