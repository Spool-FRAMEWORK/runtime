package software.spool.runtime;

import software.spool.core.adapter.logging.LoggerFactory;
import software.spool.core.adapter.otel.OTELConfig;
import software.spool.core.model.spool.SpoolNode;
import software.spool.dsl.SpoolNodeDSL;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

public class SpoolRuntimeBuilder {
    private OpenTelemetryConfiguration openTelemetryConfiguration;
    private final List<SpoolNode> nodes;
    private final List<String> dslPaths;

    public SpoolRuntimeBuilder() {
        this.nodes = new ArrayList<>();
        this.dslPaths = new ArrayList<>();
    }

    public SpoolRuntimeBuilder OpenTelemetryConfiguration(OpenTelemetryConfiguration openTelemetryConfiguration) {
        this.openTelemetryConfiguration = openTelemetryConfiguration;
        initializeOpenTelemetry();
        return this;
    }

    public SpoolRuntimeBuilder withNodeFromDSL(String path) {
        this.dslPaths.add(path);
        return this;
    }

    public SpoolRuntimeBuilder withNode(SpoolNode node) {
        nodes.add(node);
        return this;
    }

    public SpoolRuntimeBuilder withNode(List<SpoolNode> nodes) {
        nodes.forEach(this::withNode);
        return this;
    }

    /**
     * Builds the runtime with the nodes added so far and the ones loaded from the DSL descriptors.
     *
     * <p>A descriptor that cannot be loaded stops the build, because starting without its node would hide the
     * problem. The failure is logged first: logs go through OpenTelemetry, and an uncaught exception would
     * only reach stderr.</p>
     *
     * @return the runtime
     * @throws UncheckedIOException with the message of the cause when a descriptor cannot be loaded
     */
    public SpoolRuntime build() {
        initializeOpenTelemetry();
        dslPaths.forEach(p -> {
            try {
                this.nodes.add(SpoolNodeDSL.fromDescriptor(p));
            } catch (IOException e) {
                LoggerFactory.getLogger(SpoolRuntimeBuilder.class)
                        .error("Failed to load SpoolNode from DSL descriptor at path: " + p, e);
                throw new UncheckedIOException(e.getMessage(), e);
            }
        });
        return new SpoolRuntime(List.copyOf(nodes));
    }

    private void initializeOpenTelemetry() {
        OTELConfig.init(openTelemetryConfiguration.serviceName(),
                openTelemetryConfiguration.traces(),
                openTelemetryConfiguration.logs(),
                openTelemetryConfiguration.metrics());
    }
}