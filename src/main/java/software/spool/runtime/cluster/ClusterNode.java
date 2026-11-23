package software.spool.runtime.cluster;

import software.spool.core.adapter.otel.OpenTelemetryMetricsRegistry;
import software.spool.core.model.spool.SpoolModule;
import software.spool.core.model.spool.SpoolNode;
import software.spool.dsl.DescriptorLoader;
import software.spool.dsl.DescriptorModules;
import software.spool.dsl.source.TextDescriptorSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The node of a cluster: it starts with no module, answers on the port of its address and joins the Spool API, which
 * will assign it modules (ADR-022).
 */
public final class ClusterNode {
    private static final int DEFAULT_PORT = 8080;

    private ClusterNode() {}

    public static SpoolNode of(ClusterMembership membership) {
        int port = membership.address().getPort() > 0 ? membership.address().getPort() : DEFAULT_PORT;
        return SpoolNode.create(port, new OpenTelemetryMetricsRegistry());
    }

    /**
     * The heartbeat of the node, which also runs in it what the API assigns. The {@code ${env:NAME}} of each descriptor
     * are read from {@code environment}.
     */
    public static ClusterHeartbeat heartbeatOf(ClusterMembership membership, SpoolNode node,
                                               UnaryOperator<String> environment) {
        ClusterClient client = new ClusterClient(membership, Clock.systemUTC());
        AssignmentReconciler reconciler = new AssignmentReconciler(node, client,
                descriptor -> modulesOf(descriptor, environment), Clock.systemUTC());
        return new ClusterHeartbeat(client, reconciler, membership);
    }

    private static List<SpoolModule> modulesOf(String descriptor, UnaryOperator<String> environment) {
        try {
            return new DescriptorLoader(new TextDescriptorSource(descriptor), environment)
                    .load(TextDescriptorSource.LOCATION, Optional.empty(), DescriptorModules::of);
        } catch (IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
    }
}
