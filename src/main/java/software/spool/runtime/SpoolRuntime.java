package software.spool.runtime;

import software.spool.core.adapter.logging.LoggerFactory;
import software.spool.core.model.spool.SpoolNode;
import software.spool.runtime.cluster.ClusterHeartbeat;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class SpoolRuntime {
    private final List<SpoolNode> nodes;
    private final Optional<ClusterHeartbeat> cluster;

    public SpoolRuntime(List<SpoolNode> nodes) {
        this(nodes, Optional.empty());
    }

    /** A runtime whose node, once started, joins the Spool API with the heartbeat given (ADR-022). */
    public SpoolRuntime(List<SpoolNode> nodes, Optional<ClusterHeartbeat> cluster) {
        this.nodes = nodes;
        this.cluster = cluster;
    }

    public List<SpoolNode> nodes() {
        return nodes;
    }

    public static SpoolRuntimeBuilder builder() {
        return new SpoolRuntimeBuilder();
    }

    public void start() {
        nodes.forEach(n -> {
            try { n.start();
            } catch (IOException e) {
                LoggerFactory.getLogger(SpoolRuntime.class).error("Error starting SpoolRuntime: " + e.getMessage());
            }
        });
        cluster.ifPresent(ClusterHeartbeat::start);
    }

    public void stop() {
        cluster.ifPresent(ClusterHeartbeat::stop);
        nodes.forEach(SpoolNode::stop);
    }
}
