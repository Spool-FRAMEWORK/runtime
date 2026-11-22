package software.spool.runtime.cluster;

import software.spool.core.adapter.jackson.RecordSerializerFactory;
import software.spool.infrastructure.adapter.authentication.hmac.HmacSigner;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.Optional;

/**
 * The calls of a node to the Spool API, each signed with the secret of the cluster. The API declares an
 * {@code HMAC_SIGNATURE} provider with the headers {@value #SIGNATURE} and {@value #TIMESTAMP} and that secret.
 */
public final class ClusterClient {
    public static final String SIGNATURE = "X-Spool-Signature";
    public static final String TIMESTAMP = "X-Spool-Timestamp";
    private static final String NODES = "/spool/cluster/nodes";

    private final ClusterMembership membership;
    private final HmacSigner signer;
    private final HttpClient http = HttpClient.newHttpClient();

    public ClusterClient(ClusterMembership membership, Clock clock) {
        this.membership = membership;
        this.signer = new HmacSigner(SIGNATURE, membership.secret(), Optional.of(TIMESTAMP), clock);
    }

    public void register() {
        Registration registration = new Registration(membership.nodeId(), membership.address().toString());
        send("POST", NODES, RecordSerializerFactory.<Registration>record().serialize(registration), 201);
    }

    public void beat() {
        send("PUT", NODES + "/" + membership.nodeId() + "/heartbeat", new byte[0], 204);
    }

    private record Registration(String id, String address) {
    }

    private void send(String method, String path, byte[] body, int expected) {
        HttpRequest.Builder request = HttpRequest.newBuilder(membership.api().resolve(URI.create(path)))
                .method(method, body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body))
                .header("Content-Type", "application/json");
        signer.headersFor(body).forEach(request::header);
        HttpResponse<String> response = sendOrFail(request.build());
        if (response.statusCode() != expected)
            throw new ClusterException(method + " " + path + " to the Spool API answered " + response.statusCode() + ": " + response.body());
    }

    private HttpResponse<String> sendOrFail(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ClusterException("Could not reach the Spool API at " + membership.api() + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClusterException("Interrupted while calling the Spool API", e);
        }
    }
}
