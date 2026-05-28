package software.spool.runtime;

import software.spool.core.adapter.logging.LoggerFactory;
import software.spool.core.model.spool.SpoolNode;
import software.spool.dsl.SpoolNodeDSL;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SpoolRuntimeBuilder {
        private String serviceName;
        private String metricsEndpoint;
        private String logsEndpoint;
        private String tracesEndpoint;
        private final List<SpoolNode> nodes;

    public SpoolRuntimeBuilder() {
        this.nodes = new ArrayList<>();
    }

    public SpoolRuntimeBuilder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public SpoolRuntimeBuilder withNodeFromDSL(String path) {
            try {
                this.nodes.add(SpoolNodeDSL.fromDescriptor(path));
            } catch (IOException e) {
                LoggerFactory.getLogger(SpoolRuntimeBuilder.class)
                        .error("Failed to load SpoolNode from DSL descriptor at path: " + path, e);
            }
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

        public SpoolRuntimeBuilder withMetricsEndpoint(String metricsEndpoint) {
            this.metricsEndpoint = metricsEndpoint;
            return this;
        }

        public SpoolRuntimeBuilder withLogsEndpoint(String logsEndpoint) {
            this.logsEndpoint = logsEndpoint;
            return this;
        }

        public SpoolRuntimeBuilder withTracesEndpoint(String tracesEndpoint) {
            this.tracesEndpoint = tracesEndpoint;
            return this;
        }

        public SpoolRuntime build() {
            return new SpoolRuntime(buildOpenTelemetryConfiguration(), List.copyOf(nodes));
        }

    private OpenTelemetryConfiguration buildOpenTelemetryConfiguration() {
        return OpenTelemetryConfiguration.builder()
                .serviceName(serviceName)
                .metricsEndpoint(metricsEndpoint)
                .logsEndpoint(logsEndpoint)
                .tracesEndpoint(tracesEndpoint)
                .build();
    }
}