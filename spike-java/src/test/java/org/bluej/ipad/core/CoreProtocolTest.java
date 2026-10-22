package org.bluej.ipad.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.bluej.ipad.spike.InMemoryCompiler;
import org.junit.jupiter.api.Test;

class CoreProtocolTest {
    private static final String COUNTER = """
            public class Counter {
                private int count;
                public Counter(int start) { this.count = start; }
                public int increment(int amount) { this.count += amount; return this.count; }
                public int add(Counter other) { return this.count + other.count; }
                public static int sum(int[] values) {
                    int total = 0;
                    for (int value : values) { total += value; }
                    return total;
                }
                public static int divide(int a, int b) { return a / b; }
                public static long twice(long value) { return value * 2; }
                public static float half(float value) { return value / 2; }
                public static long sum(long[] values) {
                    long total = 0;
                    for (long value : values) { total += value; }
                    return total;
                }
                public static long unsupported() {
                    synchronized ("lock") { return 1; }
                }
            }
            """;

    private static CoreProtocol protocol() {
        CoreSession session = new CoreSession();
        var compilation = new InMemoryCompiler().compile("Counter", COUNTER);
        assertTrue(compilation.succeeded(), compilation.diagnostics());
        session.load(compilation.classes());
        return new CoreProtocol(session);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> call(CoreProtocol protocol, String request) {
        Object parsed = Json.parse(protocol.handle(request));
        assertInstanceOf(Map.class, parsed);
        return (Map<String, Object>) parsed;
    }

    @Test
    void listsClasses() {
        Map<String, Object> response = call(protocol(), "{\"op\":\"classes\"}");
        List<?> classes = (List<?>) response.get("classes");
        assertEquals(1, classes.size());
        Map<?, ?> counter = (Map<?, ?>) classes.get(0);
        assertEquals("Counter", counter.get("name"));
        assertFalse(((List<?>) counter.get("fields")).isEmpty());
        assertFalse(((List<?>) counter.get("methods")).isEmpty());
    }

    @Test
    void createsInvokesAndInspectsThroughJson() {
        CoreProtocol protocol = protocol();
        Map<String, Object> created = call(protocol, """
                {"op":"new","class":"Counter","args":[{"int":4}]}
                """);
        assertEquals(1, ((Number) created.get("handle")).intValue());
        assertEquals("Counter", created.get("class"));
        Map<?, ?> fields = (Map<?, ?>) created.get("fields");
        assertEquals(Map.of("int", 4L), fields.get("count"));

        Map<String, Object> invoked = call(protocol, """
                {"op":"invoke","handle":1,"method":"increment","args":[{"int":5}]}
                """);
        assertEquals(Map.of("int", 9L), invoked.get("value"));

        Map<String, Object> inspected = call(protocol, "{\"op\":\"inspect\",\"handle\":1}");
        assertEquals(Map.of("int", 9L), ((Map<?, ?>) inspected.get("fields")).get("count"));

        Map<String, Object> bench = call(protocol, "{\"op\":\"bench\"}");
        assertEquals(1, ((List<?>) bench.get("bench")).size());
    }

    @Test
    void passesBenchReferencesAndArrays() {
        CoreProtocol protocol = protocol();
        call(protocol, "{\"op\":\"new\",\"class\":\"Counter\",\"args\":[{\"int\":4}]}");
        call(protocol, "{\"op\":\"new\",\"class\":\"Counter\",\"args\":[{\"int\":6}]}");
        Map<String, Object> sum = call(protocol, """
                {"op":"invoke","handle":1,"method":"add","args":[{"ref":2}]}
                """);
        assertEquals(Map.of("int", 10L), sum.get("value"));

        Map<String, Object> array = call(protocol, """
                {"op":"invokeStatic","class":"Counter","method":"sum",
                 "args":[{"array":{"component":"int","items":[{"int":1},{"int":2},{"int":3}]}}]}
                """);
        assertEquals(Map.of("int", 6L), array.get("value"));
    }

    @Test
    void returnsErrorsInsteadOfThrowing() {
        CoreProtocol protocol = protocol();
        Map<String, Object> error = call(protocol, """
                {"op":"invokeStatic","class":"Counter","method":"divide","args":[{"int":1},{"int":0}]}
                """);
        Map<?, ?> details = (Map<?, ?>) error.get("error");
        assertEquals("java.lang.ArithmeticException", details.get("class"));
        assertEquals(false, details.get("unsupported"));

        Map<String, Object> unsupported = call(protocol, """
                {"op":"invokeStatic","class":"Counter","method":"unsupported"}
                """);
        Map<?, ?> unsupportedDetails = (Map<?, ?>) unsupported.get("error");
        assertEquals(true, unsupportedDetails.get("unsupported"));

        Map<String, Object> unknown = call(protocol, "{\"op\":\"nonsense\"}");
        assertTrue(((Map<?, ?>) unknown.get("error")).get("message").toString().contains("unknown op"));
    }

    @Test
    void carriesLongsAndFloatsAcrossTheBridge() {
        CoreProtocol protocol = protocol();
        Map<String, Object> doubled = call(protocol, """
                {"op":"invokeStatic","class":"Counter","method":"twice","args":[{"long":9000000000}]}
                """);
        assertEquals(Map.of("long", 18_000_000_000L), doubled.get("value"));

        Map<String, Object> halved = call(protocol, """
                {"op":"invokeStatic","class":"Counter","method":"half","args":[{"float":2.5}]}
                """);
        assertEquals(Map.of("float", 1.25), halved.get("value"));

        Map<String, Object> array = call(protocol, """
                {"op":"invokeStatic","class":"Counter","method":"sum",
                 "args":[{"array":{"component":"long","items":[{"long":1},{"long":2},{"long":3000000000}]}}]}
                """);
        assertEquals(Map.of("long", 3_000_000_003L), array.get("value"));
    }

    @Test
    void reportsConsoleOutput() {
        CoreProtocol protocol = protocol();
        Map<String, Object> response = call(protocol, "{\"op\":\"output\"}");
        assertEquals("", response.get("output"));
        assertEquals(true, call(protocol, "{\"op\":\"clearOutput\"}").get("cleared"));
    }

    @Test
    void quotesAndEscapesSurviveTheRoundTrip() {
        CoreProtocol protocol = protocol();
        Map<String, Object> created = call(protocol, """
                {"op":"new","class":"Counter","args":[{"int":-3}]}
                """);
        assertEquals(Map.of("int", -3L), ((Map<?, ?>) created.get("fields")).get("count"));
    }
}
