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
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** A Spool API that answers like the real one and keeps every request, with whether its signature was right. */
final class RecordingSpoolApi {
    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final HmacSignatureRequestAuthenticator authenticator;

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

    private void answer(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        HttpRequest request = new HttpRequest(HttpMethod.valueOf(exchange.getRequestMethod()), exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders(), body);
        received.add(new Received(request.method().name(), request.path(), new String(body),
                authenticator.authenticate(request) instanceof Authentication.Accepted));
        exchange.sendResponseHeaders(request.method() == HttpMethod.POST ? 201 : 204, -1);
        exchange.close();
    }

    record Received(String method, String path, String body, boolean signed) {
    }
}
