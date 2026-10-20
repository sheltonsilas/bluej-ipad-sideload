package org.bluej.ipad.server;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.bluej.ipad.core.Json;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoreServerTest {

    private CoreServer server;
    private HttpClient http;

    @BeforeEach
    void start() throws Exception {
        server = CoreServer.start(0);
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(server.baseUri() + path))
                .timeout(Duration.ofSeconds(10));
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rpc(String json) throws Exception {
        HttpResponse<String> response = request("POST", "/rpc", json);
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        return (Map<String, Object>) Json.parse(response.body());
    }

    @Test
    void reportsHealthOnTheLoopbackAddress() throws Exception {
        HttpResponse<String> health = request("GET", "/health", null);
        assertEquals(200, health.statusCode());
        Map<?, ?> body = (Map<?, ?>) Json.parse(health.body());
        assertEquals("ok", body.get("status"));
        assertEquals(0L, body.get("classes"));
    }

    @Test
    void compilesRunsAndInspectsOverHttp() throws Exception {
        String source = "public class Counter {\\n"
                + "  private int count;\\n"
                + "  public Counter(int start) { this.count = start; }\\n"
                + "  public void increment() { count++; }\\n"
                + "  public int getCount() { return count; }\\n"
                + "}\\n";
        assertEquals(true, rpc("{\"op\":\"setSource\",\"path\":\"Counter.java\",\"text\":\"" + source + "\"}")
                .get("saved"));

        Map<String, Object> compiled = rpc("{\"op\":\"compile\"}");
        assertEquals(true, compiled.get("success"));
        assertEquals(1, ((List<?>) compiled.get("classes")).size());

        Map<String, Object> created = rpc("{\"op\":\"new\",\"class\":\"Counter\",\"args\":[{\"int\":7}]}");
        assertEquals("counter1", created.get("name"));

        assertEquals(Map.of("int", 7L), rpc("{\"op\":\"invoke\",\"handle\":1,\"method\":\"getCount\"}").get("value"));
        rpc("{\"op\":\"invoke\",\"handle\":1,\"method\":\"increment\"}");
        assertEquals(Map.of("int", 8L), rpc("{\"op\":\"invoke\",\"handle\":1,\"method\":\"getCount\"}").get("value"));
    }

    @Test
    void errorsStayInBandAndMalformedRequestsAreRejected() throws Exception {
        Map<String, Object> unknown = rpc("{\"op\":\"nonsense\"}");
        assertTrue(((Map<?, ?>) unknown.get("error")).get("message").toString().contains("unknown op"));

        Map<String, Object> malformed = rpc("{not json");
        assertEquals("java.lang.IllegalArgumentException", ((Map<?, ?>) malformed.get("error")).get("class"));

        assertEquals(405, request("GET", "/rpc", null).statusCode());
        assertEquals(405, request("POST", "/health", "{}").statusCode());
    }

    @Test
    void classNameCollisionsAndUnknownClassesAreReported() throws Exception {
        rpc("{\"op\":\"setSource\",\"path\":\"Ghost.java\",\"text\":\"public class Ghost { }\"}");
        Map<String, Object> created = rpc("{\"op\":\"new\",\"class\":\"Ghost\"}");
        assertTrue(((Map<?, ?>) created.get("error")).get("message").toString().contains("not compiled"));
    }
}
