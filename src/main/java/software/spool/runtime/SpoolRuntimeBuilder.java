package software.spool.runtime;

import software.spool.core.adapter.logging.LoggerFactory;
import software.spool.core.adapter.otel.OTELConfig;
import software.spool.core.model.spool.SpoolNode;
import software.spool.dsl.SpoolNodeDSL;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SpoolRuntimeBuilder {
    private OpenTelemetryConfiguration openTelemetryConfiguration;
    private final List<SpoolNode> nodes;
    private final List<DescriptorNode> descriptorNodes;

    public SpoolRuntimeBuilder() {
        this.nodes = new ArrayList<>();
        this.descriptorNodes = new ArrayList<>();
    }

    public SpoolRuntimeBuilder OpenTelemetryConfiguration(OpenTelemetryConfiguration openTelemetryConfiguration) {
        this.openTelemetryConfiguration = openTelemetryConfiguration;
        initializeOpenTelemetry();
        return this;
    }

    /** Adds the node of a descriptor in the classpath, named by its resource path, such as {@code /Spool.yaml}. */
    public SpoolRuntimeBuilder withNodeFromDSL(String path) {
        this.descriptorNodes.add(new DescriptorNode(path, () -> SpoolNodeDSL.fromDescriptor(path)));
        return this;
    }

    /** Adds the node of a descriptor in the file system, so the same jar can run with different descriptors. */
    public SpoolRuntimeBuilder withNodeFromDSL(Path file) {
        this.descriptorNodes.add(new DescriptorNode(file.toString(), () -> SpoolNodeDSL.fromDescriptor(file)));
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
        descriptorNodes.forEach(descriptor -> {
            try {
                this.nodes.add(descriptor.loader().load());
            } catch (IOException e) {
                LoggerFactory.getLogger(SpoolRuntimeBuilder.class)
                        .error("Failed to load SpoolNode from DSL descriptor at path: " + descriptor.location(), e);
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

    private record DescriptorNode(String location, NodeLoader loader) {}

    @FunctionalInterface
    private interface NodeLoader {
        SpoolNode load() throws IOException;
    }
}
