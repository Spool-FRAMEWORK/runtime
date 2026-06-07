package software.spool.runtime;

import software.spool.core.adapter.logging.LoggerFactory;
import software.spool.core.model.spool.SpoolNode;

import java.io.IOException;
import java.util.List;

public class SpoolRuntime {
    private final List<SpoolNode> nodes;

    public SpoolRuntime(List<SpoolNode> nodes) {
        this.nodes = nodes;
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
    }
}
