package software.spool.runtime;

import org.junit.jupiter.api.Test;
import software.spool.core.adapter.otel.OTELExporterSettings;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenTelemetryConfigurationTest {

    @Test
    void build_onlyEndpoints_keepsThemAndSetsNoHeaders() {
        OpenTelemetryConfiguration configuration = OpenTelemetryConfiguration.builder()
                .serviceName("spool")
                .tracesEndpoint("http://traces")
                .logsEndpoint("http://logs")
                .metricsEndpoint("http://metrics")
                .build();

        assertThat(configuration.serviceName()).isEqualTo("spool");
        assertThat(configuration.tracesEndpoint()).isEqualTo("http://traces");
        assertThat(configuration.logsEndpoint()).isEqualTo("http://logs");
        assertThat(configuration.metricsEndpoint()).isEqualTo("http://metrics");
        assertThat(configuration.traces().headers()).isEmpty();
        assertThat(configuration.logs().headersFromEnvironment()).isEmpty();
        assertThat(configuration.metrics().compression()).isNull();
        assertThat(configuration.metrics().timeout()).isNull();
    }

    @Test
    void build_withoutAnything_leavesEveryEndpointUnset() {
        OpenTelemetryConfiguration configuration = OpenTelemetryConfiguration.builder().serviceName("spool").build();

        assertThat(configuration.tracesEndpoint()).isNull();
        assertThat(configuration.logsEndpoint()).isNull();
        assertThat(configuration.metricsEndpoint()).isNull();
    }

    @Test
    void build_commonHeaderFromEnvironment_reachesTheThreeSignals() {
        OpenTelemetryConfiguration configuration = OpenTelemetryConfiguration.builder()
                .serviceName("spool")
                .headerFromEnvironment("Authorization", "GRAFANA_OTLP_AUTHORIZATION")
                .build();

        assertThat(configuration.traces().headersFromEnvironment()).containsEntry("Authorization", "GRAFANA_OTLP_AUTHORIZATION");
        assertThat(configuration.logs().headersFromEnvironment()).containsEntry("Authorization", "GRAFANA_OTLP_AUTHORIZATION");
        assertThat(configuration.metrics().headersFromEnvironment()).containsEntry("Authorization", "GRAFANA_OTLP_AUTHORIZATION");
    }

    @Test
    void build_commonHeader_reachesTheThreeSignals() {
        OpenTelemetryConfiguration configuration = OpenTelemetryConfiguration.builder()
                .serviceName("spool")
                .header("X-Scope-OrgID", "tenant")
                .build();

        assertThat(configuration.traces().headers()).containsEntry("X-Scope-OrgID", "tenant");
        assertThat(configuration.logs().headers()).containsEntry("X-Scope-OrgID", "tenant");
        assertThat(configuration.metrics().headers()).containsEntry("X-Scope-OrgID", "tenant");
    }

    @Test
    void build_signalSettings_overrideTheCommonHeaderOnlyForThatSignal() {
        OpenTelemetryConfiguration configuration = OpenTelemetryConfiguration.builder()
                .serviceName("spool")
                .headerFromEnvironment("Authorization", "COMMON_AUTHORIZATION")
                .traces(OTELExporterSettings.builder().headerFromEnvironment("Authorization", "TRACES_AUTHORIZATION").build())
                .build();

        assertThat(configuration.traces().headersFromEnvironment()).containsEntry("Authorization", "TRACES_AUTHORIZATION");
        assertThat(configuration.logs().headersFromEnvironment()).containsEntry("Authorization", "COMMON_AUTHORIZATION");
        assertThat(configuration.metrics().headersFromEnvironment()).containsEntry("Authorization", "COMMON_AUTHORIZATION");
    }

    @Test
    void build_signalSettings_addTheirHeadersToTheCommonOnes() {
        OpenTelemetryConfiguration configuration = OpenTelemetryConfiguration.builder()
                .serviceName("spool")
                .header("X-Common", "1")
                .logs(OTELExporterSettings.builder().header("X-Logs", "2").build())
                .build();

        assertThat(configuration.logs().headers()).containsEntry("X-Common", "1").containsEntry("X-Logs", "2");
        assertThat(configuration.traces().headers()).containsOnlyKeys("X-Common");
    }

    @Test
    void build_endpointMethod_winsOverTheEndpointOfTheSignalSettings() {
        OpenTelemetryConfiguration configuration = OpenTelemetryConfiguration.builder()
                .serviceName("spool")
                .traces(OTELExporterSettings.builder().endpoint("http://from-settings").build())
                .tracesEndpoint("http://from-method")
                .build();

        assertThat(configuration.tracesEndpoint()).isEqualTo("http://from-method");
    }

    @Test
    void build_signalSettingsEndpoint_isKeptWhenNoEndpointMethodIsCalled() {
        OpenTelemetryConfiguration configuration = OpenTelemetryConfiguration.builder()
                .serviceName("spool")
                .traces(OTELExporterSettings.builder().endpoint("http://from-settings").build())
                .build();

        assertThat(configuration.tracesEndpoint()).isEqualTo("http://from-settings");
    }

    @Test
    void build_commonCompressionAndTimeout_areInheritedByEverySignal() {
        OpenTelemetryConfiguration configuration = OpenTelemetryConfiguration.builder()
                .serviceName("spool")
                .compression("gzip")
                .timeout(Duration.ofSeconds(5))
                .build();

        assertThat(configuration.traces().compression()).isEqualTo("gzip");
        assertThat(configuration.logs().compression()).isEqualTo("gzip");
        assertThat(configuration.metrics().timeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void build_unsupportedCompression_failsWhenBuilding() {
        OpenTelemetryConfiguration.Builder builder = OpenTelemetryConfiguration.builder()
                .serviceName("spool")
                .compression("zstd");

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zstd");
    }
}
