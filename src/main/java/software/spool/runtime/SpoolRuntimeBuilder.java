package software.spool.runtime;

import software.spool.core.adapter.logging.LoggerFactory;
import software.spool.core.adapter.otel.OTELConfig;
import software.spool.core.model.spool.SpoolNode;
import software.spool.dsl.SpoolNodeDSL;

import java.io.IOException;
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

    public SpoolRuntime build() {
        initializeOpenTelemetry();
        dslPaths.forEach(p -> {
            try {
                this.nodes.add(SpoolNodeDSL.fromDescriptor(p));
            } catch (IOException e) {
                LoggerFactory.getLogger(SpoolRuntimeBuilder.class)
                        .error("Failed to load SpoolNode from DSL descriptor at path: " + p, e);
            }
        });
        return new SpoolRuntime(List.copyOf(nodes));
    }

    private void initializeOpenTelemetry() {
        OTELConfig.init(openTelemetryConfiguration.serviceName(),
                openTelemetryConfiguration.tracesEndpoint(),
                openTelemetryConfiguration.logsEndpoint(),
                openTelemetryConfiguration.metricsEndpoint());
    }
}