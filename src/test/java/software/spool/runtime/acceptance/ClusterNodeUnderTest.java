package software.spool.runtime.acceptance;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.opentelemetry.api.GlobalOpenTelemetry;
import software.spool.runtime.OpenTelemetryConfiguration;
import software.spool.runtime.SpoolRuntime;
import software.spool.runtime.SpoolRuntimeBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** The node under test, its environment and the Spool API it talks to, shared by the steps of a scenario. */
public class ClusterNodeUnderTest {
    static final String SECRET = "cluster-secret";
    private static final OpenTelemetryConfiguration OPEN_TELEMETRY = OpenTelemetryConfiguration.builder()
            .serviceName("spool-runtime-cluster").build();

    final Map<String, String> environment = new HashMap<>();
    Optional<RecordingSpoolApi> api = Optional.empty();
    Optional<SpoolRuntime> runtime = Optional.empty();
    Optional<RuntimeException> failure = Optional.empty();

    @Before
    public void resetOpenTelemetry() {
        GlobalOpenTelemetry.resetForTest();
    }

    @After
    public void stop() {
        runtime.ifPresent(SpoolRuntime::stop);
        api.ifPresent(RecordingSpoolApi::stop);
    }

    RecordingSpoolApi startApi() throws IOException {
        RecordingSpoolApi started = new RecordingSpoolApi(SECRET);
        api = Optional.of(started);
        return started;
    }

    SpoolRuntimeBuilder builder() {
        return SpoolRuntime.builder().OpenTelemetryConfiguration(OPEN_TELEMETRY).withEnvironment(environment::get);
    }

    void start(SpoolRuntimeBuilder builder) {
        try {
            runtime = Optional.of(builder.build());
            runtime.get().start();
        } catch (RuntimeException e) {
            failure = Optional.of(e);
        }
    }
}
