package software.spool.runtime.cluster;

import software.spool.dsl.reader.ValueParsers;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * How a node takes part in a cluster of the Spool API (ADR-022): where the API is, the id and address of the node, the
 * secret it signs with and how often it says it is alive. Read from the environment: with none of the variables the
 * node is not in a cluster, and with only some of them it cannot start.
 */
public record ClusterMembership(URI api, String nodeId, URI address, String secret, Duration heartbeatInterval) {
    public static final String API = "SPOOL_CLUSTER_API";
    public static final String NODE_ID = "SPOOL_NODE_ID";
    public static final String NODE_ADDRESS = "SPOOL_NODE_ADDRESS";
    public static final String SECRET = "SPOOL_CLUSTER_SECRET";
    public static final String HEARTBEAT = "SPOOL_CLUSTER_HEARTBEAT";
    private static final List<String> REQUIRED = List.of(API, NODE_ID, NODE_ADDRESS, SECRET);
    private static final Duration DEFAULT_HEARTBEAT = Duration.ofSeconds(10);

    public static Optional<ClusterMembership> fromTheEnvironment(UnaryOperator<String> environment) {
        List<String> missing = REQUIRED.stream().filter(name -> valueOf(environment, name).isEmpty()).toList();
        if (missing.size() == REQUIRED.size()) return Optional.empty();
        if (!missing.isEmpty()) throw new IllegalStateException("Cluster mode needs " + String.join(", ", missing)
                + " too: set every one of " + String.join(", ", REQUIRED) + ", or none to start from a descriptor");
        return Optional.of(new ClusterMembership(URI.create(environment.apply(API)), environment.apply(NODE_ID),
                URI.create(environment.apply(NODE_ADDRESS)), environment.apply(SECRET),
                heartbeatOf(environment)));
    }

    private static Duration heartbeatOf(UnaryOperator<String> environment) {
        return valueOf(environment, HEARTBEAT).map(value -> ValueParsers.DURATION.parse(value).orElseThrow(() ->
                new IllegalStateException(HEARTBEAT + " must be " + ValueParsers.DURATION.expected() + ", got '" + value + "'")))
                .orElse(DEFAULT_HEARTBEAT);
    }

    private static Optional<String> valueOf(UnaryOperator<String> environment, String name) {
        return Optional.ofNullable(environment.apply(name)).filter(value -> !value.isBlank());
    }
}
