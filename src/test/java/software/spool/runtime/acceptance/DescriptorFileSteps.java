package software.spool.runtime.acceptance;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.opentelemetry.api.GlobalOpenTelemetry;
import software.spool.runtime.OpenTelemetryConfiguration;
import software.spool.runtime.SpoolRuntime;
import software.spool.runtime.SpoolRuntimeBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class DescriptorFileSteps {
    private static final OpenTelemetryConfiguration OPEN_TELEMETRY = OpenTelemetryConfiguration.builder()
            .serviceName("spool-runtime-acceptance")
            .build();

    private static final String VALID_DESCRIPTOR = """
            infrastructure:
              eventBus:
                kind: EVENT_BUS
                type: IN_MEMORY

            modules: []
            """;

    private static final String CRAWLER_WITHOUT_SOURCE = """
            infrastructure:
              eventBus:
                kind: EVENT_BUS
                type: IN_MEMORY
              inbox:
                kind: INBOX
                type: IN_MEMORY

            modules:
              - crawler:
                  type: POLL
                  id: broken-crawler
                  eventMapping:
                    namingConvention: SNAKE_CASE
            """;

    private Path folder;
    private Optional<SpoolRuntime> runtime = Optional.empty();
    private Optional<UncheckedIOException> failure = Optional.empty();

    @Before
    public void createFolderOutsideTheClasspath() throws IOException {
        GlobalOpenTelemetry.resetForTest();
        folder = Files.createTempDirectory("spool-descriptor");
    }

    @After
    public void deleteFolder() throws IOException {
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
    }

    @Given("a valid descriptor {string} in a folder outside the classpath")
    public void aValidDescriptor(String name) throws IOException {
        Files.writeString(folder.resolve(name), VALID_DESCRIPTOR);
    }

    @Given("a descriptor {string} in a folder outside the classpath with a crawler without source")
    public void aDescriptorWithACrawlerWithoutSource(String name) throws IOException {
        Files.writeString(folder.resolve(name), CRAWLER_WITHOUT_SOURCE);
    }

    @Given("a folder outside the classpath with the descriptors {string} on port {int} and {string} on port {int}")
    public void aFolderWithTheDescriptors(String first, int firstPort, String second, int secondPort) throws IOException {
        Files.createDirectories(descriptorFolder());
        Files.writeString(descriptorFolder().resolve(first), onPort(firstPort));
        Files.writeString(descriptorFolder().resolve(second), onPort(secondPort));
    }

    @Given("a descriptor {string} on port {int} in a folder outside the classpath")
    public void aDescriptorOnPort(String name, int port) throws IOException {
        Files.writeString(folder.resolve(name), onPort(port));
    }

    @When("the runtime is built from that folder")
    public void theRuntimeIsBuiltFromThatFolder() {
        build(SpoolRuntime.builder().OpenTelemetryConfiguration(OPEN_TELEMETRY).withNodesFromDSL(descriptorFolder()));
    }

    @When("the runtime is built from that folder and from the absolute path of {string}")
    public void theRuntimeIsBuiltFromThatFolderAndFromTheAbsolutePathOf(String name) {
        build(SpoolRuntime.builder().OpenTelemetryConfiguration(OPEN_TELEMETRY)
                .withNodesFromDSL(descriptorFolder())
                .withNodeFromDSL(absolutePathOf(name)));
    }

    @Then("the runtime has {int} nodes")
    public void theRuntimeHasNodes(int count) {
        assertThat(failure).isEmpty();
        assertThat(runtime).hasValueSatisfying(built -> assertThat(built.nodes()).hasSize(count));
    }

    @When("the runtime is built from the absolute path of {string}")
    public void theRuntimeIsBuiltFromTheAbsolutePath(String name) {
        build(SpoolRuntime.builder().OpenTelemetryConfiguration(OPEN_TELEMETRY)
                .withNodeFromDSL(absolutePathOf(name)));
    }

    @When("the runtime is built from the classpath resource {string}")
    public void theRuntimeIsBuiltFromTheClasspathResource(String resource) {
        build(SpoolRuntime.builder().OpenTelemetryConfiguration(OPEN_TELEMETRY).withNodeFromDSL(resource));
    }

    @Then("the runtime is built")
    public void theRuntimeIsBuilt() {
        assertThat(failure).isEmpty();
        assertThat(runtime).isPresent();
    }

    @Then("building fails saying {string}")
    public void buildingFailsSaying(String text) {
        assertThat(failure).hasValueSatisfying(error -> assertThat(error).hasMessageContaining(text));
    }

    @Then("the error names the path of {string}")
    public void theErrorNamesThePathOf(String name) {
        assertThat(failure).hasValueSatisfying(
                error -> assertThat(error).hasMessageContaining(absolutePathOf(name).toString()));
    }

    private void build(SpoolRuntimeBuilder builder) {
        try {
            runtime = Optional.of(builder.build());
        } catch (UncheckedIOException error) {
            failure = Optional.of(error);
        }
    }

    private Path descriptorFolder() {
        return folder.resolve("pipelines");
    }

    private static String onPort(int port) {
        return "server: {port: " + port + "}\n" + VALID_DESCRIPTOR;
    }

    private Path absolutePathOf(String name) {
        return folder.resolve(name).toAbsolutePath().normalize();
    }
}
