package software.spool.runtime;

import io.opentelemetry.api.GlobalOpenTelemetry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpoolRuntimeBuilderTest {

    private static final OpenTelemetryConfiguration OTEL = OpenTelemetryConfiguration.builder()
        .serviceName("spool-runtime-test")
        .build();

    /** Other tests in the same JVM may have registered the global OpenTelemetry, which the builder registers again. */
    @BeforeAll
    static void freshGlobalOpenTelemetry() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void build_withoutDescriptors_returnsARuntime() {
        assertThat(SpoolRuntime.builder().OpenTelemetryConfiguration(OTEL).build()).isNotNull();
    }

    @Test
    void build_withADescriptorThatDoesNotExist_failsInsteadOfStartingWithoutIt() {
        SpoolRuntimeBuilder builder = SpoolRuntime.builder()
            .OpenTelemetryConfiguration(OTEL)
            .withNodeFromDSL("/descriptors/missing.yaml");

        assertThatThrownBy(builder::build)
            .isInstanceOf(UncheckedIOException.class)
            .hasMessageContaining("/descriptors/missing.yaml")
            .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void build_withADescriptorThatIsInvalid_failsAndSaysWhichKeyIsWrong() {
        SpoolRuntimeBuilder builder = SpoolRuntime.builder()
            .OpenTelemetryConfiguration(OTEL)
            .withNodeFromDSL("/descriptors/crawler-without-source.yaml");

        assertThatThrownBy(builder::build)
            .isInstanceOf(UncheckedIOException.class)
            .hasMessageContaining("modules[0].crawler.source is required");
    }
}
