package software.spool.runtime;

import software.spool.core.adapter.logging.LoggerFactory;
import software.spool.core.adapter.otel.OTELConfig;
import software.spool.core.model.spool.SpoolNode;
import software.spool.core.port.logging.Logger;
import software.spool.dsl.DescriptorFolder;
import software.spool.dsl.DescriptorLoader;
import software.spool.dsl.DistinctPorts;
import software.spool.dsl.NamedDescriptor;
import software.spool.dsl.SpoolNodeDSL;
import software.spool.dsl.source.FileSystemDescriptorSource;
import software.spool.runtime.cluster.ClusterMembership;
import software.spool.runtime.cluster.ClusterNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class SpoolRuntimeBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpoolRuntimeBuilder.class);

    private OpenTelemetryConfiguration openTelemetryConfiguration;
    private final List<SpoolNode> nodes;
    private final List<DescriptorEntry> descriptors;
    private UnaryOperator<String> environment = System::getenv;

    public SpoolRuntimeBuilder() {
        this.nodes = new ArrayList<>();
        this.descriptors = new ArrayList<>();
    }

    public SpoolRuntimeBuilder OpenTelemetryConfiguration(OpenTelemetryConfiguration openTelemetryConfiguration) {
        this.openTelemetryConfiguration = openTelemetryConfiguration;
        initializeOpenTelemetry();
        return this;
    }

    /** Adds the node of a descriptor in the classpath, named by its resource path, such as {@code /Spool.yaml}. */
    public SpoolRuntimeBuilder withNodeFromDSL(String path) {
        this.descriptors.add(new DescriptorEntry(path, () -> single(path, DescriptorLoader.fromClasspath())));
        return this;
    }

    /** Adds the node of a descriptor in the file system, so the same jar can run with different descriptors. */
    public SpoolRuntimeBuilder withNodeFromDSL(Path file) {
        String location = FileSystemDescriptorSource.locationOf(file);
        this.descriptors.add(new DescriptorEntry(location, () -> single(location, DescriptorLoader.fromFileSystem())));
        return this;
    }

    /** Adds a node for every {@code .spool.yaml} of a folder of the file system and its subfolders. */
    public SpoolRuntimeBuilder withNodesFromDSL(Path folder) {
        String location = FileSystemDescriptorSource.locationOf(folder);
        this.descriptors.add(new DescriptorEntry(location, () -> ofFolder(location, DescriptorFolder.fromFileSystem())));
        return this;
    }

    /** Adds a node for every {@code .spool.yaml} of a folder of the classpath, such as {@code /pipelines}, also in a jar. */
    public SpoolRuntimeBuilder withNodesFromDSL(String folder) {
        this.descriptors.add(new DescriptorEntry(folder, () -> ofFolder(folder, DescriptorFolder.fromClasspath())));
        return this;
    }

    /** Where the cluster variables of {@link ClusterMembership} are read; the environment of the process by default. */
    public SpoolRuntimeBuilder withEnvironment(UnaryOperator<String> environment) {
        this.environment = environment;
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
     * problem, and so do two descriptors on the same port, wherever each comes from. The failure is logged first:
     * logs go through OpenTelemetry, and an uncaught exception would only reach stderr.</p>
     *
     * @return the runtime
     * @throws UncheckedIOException with the message of the cause when a descriptor cannot be loaded
     */
    public SpoolRuntime build() {
        initializeOpenTelemetry();
        Optional<ClusterMembership> membership = ClusterMembership.fromTheEnvironment(environment);
        if (membership.isPresent()) return clusterRuntime(membership.get());
        List<NamedDescriptor> loaded = new ArrayList<>();
        for (DescriptorEntry descriptor : descriptors) {
            loaded.addAll(loadedOrFail(descriptor.location(), descriptor.loader()));
        }
        checkPorts(loaded);
        loaded.forEach(named -> {
            LOGGER.info("Node from {} on port {}", named.name(), named.descriptor().server().port());
            nodes.add(SpoolNodeDSL.fromDescriptor(named.descriptor()));
        });
        return new SpoolRuntime(List.copyOf(nodes));
    }

    private SpoolRuntime clusterRuntime(ClusterMembership membership) {
        LOGGER.info("Node {} joins the cluster of the Spool API at {}; its descriptors are not loaded, the API assigns its modules",
                membership.nodeId(), membership.api());
        SpoolNode node = ClusterNode.of(membership);
        return new SpoolRuntime(List.of(node), Optional.of(ClusterNode.heartbeatOf(membership, node, environment)));
    }

    private void initializeOpenTelemetry() {
        OTELConfig.init(openTelemetryConfiguration.serviceName(),
                openTelemetryConfiguration.traces(),
                openTelemetryConfiguration.logs(),
                openTelemetryConfiguration.metrics());
    }

    private static List<NamedDescriptor> loadedOrFail(String location, DescriptorsLoader loader) {
        try {
            return loader.load();
        } catch (IOException e) {
            LOGGER.error("Failed to load SpoolNode from DSL descriptor at path: " + location, e);
            throw new UncheckedIOException(e.getMessage(), e);
        }
    }

    private static void checkPorts(List<NamedDescriptor> loaded) {
        try {
            DistinctPorts.check(loaded);
        } catch (IOException e) {
            LOGGER.error("Two DSL descriptors of the runtime open the same port", e);
            throw new UncheckedIOException(e.getMessage(), e);
        }
    }

    private static List<NamedDescriptor> single(String location, DescriptorLoader loader) throws IOException {
        return List.of(new NamedDescriptor(location, loader.load(location, loader.profileOfTheEnvironment(), Function.identity())));
    }

    private static List<NamedDescriptor> ofFolder(String folder, DescriptorFolder descriptors) throws IOException {
        return descriptors.load(folder, descriptors.profileOfTheEnvironment());
    }

    private record DescriptorEntry(String location, DescriptorsLoader loader) {}

    @FunctionalInterface
    private interface DescriptorsLoader {
        List<NamedDescriptor> load() throws IOException;
    }
}
