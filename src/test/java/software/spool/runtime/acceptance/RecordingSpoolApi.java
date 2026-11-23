package software.spool.runtime.acceptance;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import software.spool.core.port.authentication.Authentication;
import software.spool.core.port.http.HttpMethod;
import software.spool.core.port.http.HttpRequest;
import software.spool.infrastructure.adapter.authentication.hmac.HmacSignatureRequestAuthenticator;
import software.spool.runtime.cluster.ClusterClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A Spool API that answers like the real one and keeps every request, with whether its signature was right. It
 * assigns to each node what a step tells it to, as the real one would answer it.
 */
final class RecordingSpoolApi {
    private static final String ASSIGNMENTS = "/assignments";
    private static final String NOTHING = "{\"leaseSeconds\":30,\"deployments\":[]}";

    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final HmacSignatureRequestAuthenticator authenticator;
    private final Map<String, String> assignmentsByNode = new ConcurrentHashMap<>();

    RecordingSpoolApi(String secret) throws IOException {
        authenticator = new HmacSignatureRequestAuthenticator("nodes", ClusterClient.SIGNATURE, secret,
                Optional.of(ClusterClient.TIMESTAMP), Duration.ofMinutes(5), Set.of("node"), Clock.systemUTC());
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::answer);
        server.start();
    }

    URI address() {
        return URI.create("http://localhost:" + server.getAddress().getPort());
    }

    List<Received> received() {
        return List.copyOf(received);
    }

    void stop() {
        server.stop(0);
    }

    /** What the API answers when the node asks for its assignments, as JSON. */
    void assign(String node, String assignmentsJson) {
        assignmentsByNode.put(node, assignmentsJson);
    }

    private void answer(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        HttpRequest request = new HttpRequest(HttpMethod.valueOf(exchange.getRequestMethod()), exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders(), body);
        received.add(new Received(request.method().name(), request.path(), new String(body),
                authenticator.authenticate(request) instanceof Authentication.Accepted));
        if (request.method() == HttpMethod.GET && request.path().endsWith(ASSIGNMENTS)) {
            String node = request.path().split("/")[4];
            byte[] answer = assignmentsByNode.getOrDefault(node, NOTHING).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, answer.length);
            exchange.getResponseBody().write(answer);
        } else {
            exchange.sendResponseHeaders(request.method() == HttpMethod.POST ? 201 : 204, -1);
        }
        exchange.close();
    }

    record Received(String method, String path, String body, boolean signed) {
    }
}
