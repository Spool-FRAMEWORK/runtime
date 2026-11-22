package software.spool.runtime.cluster;

import software.spool.core.adapter.otel.OpenTelemetryMetricsRegistry;
import software.spool.core.model.spool.SpoolNode;

import java.time.Clock;

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

    public static ClusterHeartbeat heartbeatOf(ClusterMembership membership) {
        return new ClusterHeartbeat(new ClusterClient(membership, Clock.systemUTC()), membership);
    }
}
