package org.bluej.ipad.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import org.bluej.ipad.core.CoreProtocol;
import org.bluej.ipad.core.CoreSession;

/**
 * Loopback HTTP transport for {@link CoreSession}.
 *
 * <p>The iPad app talks to the core through {@link CoreProtocol} JSON in every build. On a
 * device that call is a C function; on the simulator and on a Mac it is a POST to this
 * server, so the app under test exercises the same protocol, compiler and interpreter as the
 * product will without a signed device build in the loop.
 *
 * <pre>
 * GET  /health   {"status":"ok","protocol":1,"classes":0}
 * POST /rpc      request JSON -&gt; response JSON
 * </pre>
 *
 * <p>Requests are served one at a time, matching the single-outstanding-request contract of
 * the protocol and keeping the interpreter's state deterministic.
 */
public final class CoreServer implements AutoCloseable {

    public static final int DEFAULT_PORT = 8899;
    private static final int MAX_BODY_BYTES = 4 * 1024 * 1024;

    private final HttpServer server;
    private final CoreSession session;
    private final CoreProtocol protocol;
    private final int port;

    private CoreServer(HttpServer server, CoreSession session, CoreProtocol protocol, int port) {
        this.server = server;
        this.session = session;
        this.protocol = protocol;
        this.port = port;
    }

    public static CoreServer start(int port) throws IOException {
        return start(port, new CoreSession());
    }

    public static CoreServer start(int port, CoreSession session) throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 16);
        CoreProtocol protocol = new CoreProtocol(session);
        server.createContext("/health", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":{\"class\":\"java.lang.IllegalArgumentException\","
                        + "\"message\":\"use GET\",\"unsupported\":false}}");
                return;
            }
            String body = "{\"status\":\"ok\",\"protocol\":1,\"classes\":"
                    + session.classes().size() + "}";
            respond(exchange, 200, body);
        });
        server.createContext("/rpc", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":{\"class\":\"java.lang.IllegalArgumentException\","
                        + "\"message\":\"use POST\",\"unsupported\":false}}");
                return;
            }
            byte[] body;
            try {
                body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
            } catch (IOException failure) {
                respond(exchange, 400, "\"malformed body\"");
                return;
            }
            if (body.length > MAX_BODY_BYTES) {
                respond(exchange, 413, "{\"error\":{\"class\":\"java.lang.IllegalArgumentException\","
                        + "\"message\":\"request too large\",\"unsupported\":false}}");
                return;
            }
            String request = new String(body, StandardCharsets.UTF_8);
            String response;
            long started = System.nanoTime();
            try {
                response = protocol.handle(request);
                System.out.println("[rpc] " + summarize(request) + " ("
                        + (System.nanoTime() - started) / 1_000_000 + " ms) -> " + summarize(response));
            } catch (RuntimeException failure) {
                response = "{\"error\":{\"class\":\"" + failure.getClass().getName()
                        + "\",\"message\":\"server failure\",\"unsupported\":false}}";
            }
            respond(exchange, 200, response);
        });
        server.createContext("/", exchange -> {
            if (!"/".equals(exchange.getRequestURI().getPath())) {
                respond(exchange, 404, "{}");
                return;
            }
            respond(exchange, 200, "BlueJ iPad core server\nPOST /rpc with protocol JSON, GET /health\n");
        });
        server.setExecutor(Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "bluej-core");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        return new CoreServer(server, session, protocol, server.getAddress().getPort());
    }

    public int port() { return port; }

    public java.net.URI baseUri() {
        return java.net.URI.create("http://127.0.0.1:" + port);
    }

    public CoreSession session() { return session; }

    /**
     * Compiles a throwaway class so the first real compile does not pay javac's cold-start
     * cost. The app compiles as soon as a project opens, and the jar's first compile can take
     * twenty seconds, so this runs before the server starts accepting requests.
     */
    public static void warmUp(CoreSession session) {
        long started = System.nanoTime();
        session.setSource("__Warmup.java", "public class __Warmup { public int value() { return 1; } }");
        CoreSession.CompileReport report = session.compile();
        session.deleteSource("__Warmup.java");
        session.compile();
        System.out.println("warmed up in " + (System.nanoTime() - started) / 1_000_000
                + " ms (compile ok: " + report.success() + ")");
    }

    private static String summarize(String json) {
        String single = json.replace('\n', ' ').replace('\r', ' ').trim();
        return single.length() <= 220 ? single : single.substring(0, 220) + "...";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type",
                body.startsWith("{") || body.startsWith("[")
                        ? "application/json; charset=utf-8" : "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public static void main(String[] args) throws Exception {
        int port = DEFAULT_PORT;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--port") && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--help")) {
                System.out.println("usage: core-server [--port N]");
                return;
            }
        }
        CoreSession session = new CoreSession();
        warmUp(session);
        CoreServer server = start(port, session);
        System.out.println("BlueJ iPad core listening on " + server.baseUri());
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        Thread.currentThread().join();
    }

    /** Diagnostic metadata for the health endpoint and logs. */
    public Map<String, Object> info() {
        return Map.of("port", port, "protocol", 1);
    }
}
