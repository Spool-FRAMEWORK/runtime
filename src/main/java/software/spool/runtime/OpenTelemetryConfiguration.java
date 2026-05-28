package software.spool.runtime;

public record OpenTelemetryConfiguration(
        String serviceName,
        String metricsEndpoint,
        String logsEndpoint,
        String tracesEndpoint
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String serviceName;
        private String metricsEndpoint;
        private String logsEndpoint;
        private String tracesEndpoint;

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

        public OpenTelemetryConfiguration build() {
            return new OpenTelemetryConfiguration(serviceName, metricsEndpoint, logsEndpoint, tracesEndpoint);
        }
    }
}
