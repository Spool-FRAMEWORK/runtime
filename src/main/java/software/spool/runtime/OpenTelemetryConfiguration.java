package software.spool.runtime;

import software.spool.core.adapter.otel.OTELExporterSettings;

import java.time.Duration;

public record OpenTelemetryConfiguration(
        String serviceName,
        OTELExporterSettings metrics,
        OTELExporterSettings logs,
        OTELExporterSettings traces
) {
    public String metricsEndpoint() {
        return metrics.endpoint();
    }

    public String logsEndpoint() {
        return logs.endpoint();
    }

    public String tracesEndpoint() {
        return traces.endpoint();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String serviceName;
        private String metricsEndpoint;
        private String logsEndpoint;
        private String tracesEndpoint;
        private final OTELExporterSettings.Builder common = OTELExporterSettings.builder();
        private OTELExporterSettings metrics = OTELExporterSettings.none();
        private OTELExporterSettings logs = OTELExporterSettings.none();
        private OTELExporterSettings traces = OTELExporterSettings.none();

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder metricsEndpoint(String metricsEndpoint) {
            this.metricsEndpoint = metricsEndpoint;
            return this;
        }

        public Builder logsEndpoint(String logsEndpoint) {
            this.logsEndpoint = logsEndpoint;
            return this;
        }

        public Builder tracesEndpoint(String tracesEndpoint) {
            this.tracesEndpoint = tracesEndpoint;
            return this;
        }

        public Builder header(String name, String value) {
            common.header(name, value);
            return this;
        }

        public Builder headerFromEnvironment(String name, String variable) {
            common.headerFromEnvironment(name, variable);
            return this;
        }

        public Builder compression(String compression) {
            common.compression(compression);
            return this;
        }

        public Builder timeout(Duration timeout) {
            common.timeout(timeout);
            return this;
        }

        public Builder metrics(OTELExporterSettings metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder logs(OTELExporterSettings logs) {
            this.logs = logs;
            return this;
        }

        public Builder traces(OTELExporterSettings traces) {
            this.traces = traces;
            return this;
        }

        public OpenTelemetryConfiguration build() {
            OTELExporterSettings base = common.build();
            return new OpenTelemetryConfiguration(serviceName,
                    metrics.withEndpoint(metricsEndpoint).overriding(base),
                    logs.withEndpoint(logsEndpoint).overriding(base),
                    traces.withEndpoint(tracesEndpoint).overriding(base));
        }
    }
}
