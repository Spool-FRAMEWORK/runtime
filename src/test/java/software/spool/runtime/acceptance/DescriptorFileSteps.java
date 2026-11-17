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
                type: IN_MEMORY

            modules: []
            """;

    private static final String CRAWLER_WITHOUT_SOURCE = """
            infrastructure:
              eventBus:
                type: IN_MEMORY
              inbox:
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

    private Path absolutePathOf(String name) {
        return folder.resolve(name).toAbsolutePath().normalize();
    }
}
