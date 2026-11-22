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
import software.spool.runtime.cluster.ClusterMembership;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class ClusterSteps {
    private static final String SECRET = "cluster-secret";
    private static final OpenTelemetryConfiguration OPEN_TELEMETRY = OpenTelemetryConfiguration.builder()
            .serviceName("spool-runtime-cluster").build();

    private final Map<String, String> environment = new HashMap<>();
    private Optional<RecordingSpoolApi> api = Optional.empty();
    private Optional<SpoolRuntime> runtime = Optional.empty();
    private Optional<RuntimeException> failure = Optional.empty();

    @Before
    public void resetOpenTelemetry() {
        GlobalOpenTelemetry.resetForTest();
    }

    @After
    public void stop() {
        runtime.ifPresent(SpoolRuntime::stop);
        api.ifPresent(RecordingSpoolApi::stop);
    }

    @Given("a Spool API that records what it receives")
    public void aRecordingApi() throws IOException {
        api = Optional.of(new RecordingSpoolApi(SECRET));
    }

    @Given("the environment has the cluster variables for {string} at {string}")
    public void theClusterVariables(String nodeId, String address) throws IOException {
        environment.put(ClusterMembership.API, api.map(RecordingSpoolApi::address).orElse(URI.create("http://localhost:1")).toString());
        environment.put(ClusterMembership.NODE_ID, nodeId);
        environment.put(ClusterMembership.NODE_ADDRESS, onAFreePort(address));
        environment.put(ClusterMembership.SECRET, SECRET);
    }

    @Given("the environment has the cluster variables for {string} at {string} but not SPOOL_CLUSTER_SECRET")
    public void theClusterVariablesButTheSecret(String nodeId, String address) throws IOException {
        theClusterVariables(nodeId, address);
        environment.remove(ClusterMembership.SECRET);
    }

    @Given("the environment has no cluster variables")
    public void noClusterVariables() {
        environment.clear();
    }

    @When("the node starts with a heartbeat every {int} second(s)")
    public void theNodeStartsWithAHeartbeat(int seconds) {
        environment.put(ClusterMembership.HEARTBEAT, seconds + "s");
        start(builder());
    }

    @When("the node starts from the descriptor {string}")
    public void theNodeStartsFromTheDescriptor(String resource) {
        start(builder().withNodeFromDSL("/" + resource));
    }

    @When("the node starts")
    public void theNodeStarts() {
        start(builder());
    }

    @Then("the API received the registration of {string} at {string}, signed with the secret")
    public void theApiReceivedTheRegistration(String nodeId, String address) {
        assertThat(failure).isEmpty();
        assertThat(api.orElseThrow().received()).filteredOn(request -> request.method().equals("POST"))
                .singleElement().satisfies(request -> {
                    assertThat(request.path()).isEqualTo("/spool/cluster/nodes");
                    assertThat(request.body()).contains("\"id\":\"" + nodeId + "\"").contains(environment.get(ClusterMembership.NODE_ADDRESS));
                    assertThat(request.signed()).isTrue();
                });
    }

    @Then("within {int} seconds the API received at least {int} heartbeats of {string}, signed with the secret")
    public void theApiReceivedHeartbeats(int seconds, int count, String nodeId) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(seconds);
        while (heartbeatsOf(nodeId) < count && Instant.now().isBefore(deadline)) Thread.sleep(100);
        assertThat(heartbeatsOf(nodeId)).isGreaterThanOrEqualTo(count);
    }

    @Then("the node runs the modules of the descriptor")
    public void theNodeRunsTheDescriptor() {
        assertThat(failure).isEmpty();
        assertThat(runtime.orElseThrow().nodes()).hasSize(1);
    }

    @Then("the API received nothing")
    public void theApiReceivedNothing() {
        assertThat(api.orElseThrow().received()).isEmpty();
    }

    @Then("the start fails with an error that says {string}")
    public void theStartFails(String text) {
        assertThat(failure).hasValueSatisfying(error -> assertThat(error).hasMessageContaining(text));
    }

    private long heartbeatsOf(String nodeId) {
        return api.orElseThrow().received().stream()
                .filter(request -> request.method().equals("PUT"))
                .filter(request -> request.path().equals("/spool/cluster/nodes/" + nodeId + "/heartbeat"))
                .filter(RecordingSpoolApi.Received::signed)
                .count();
    }

    private SpoolRuntimeBuilder builder() {
        return SpoolRuntime.builder().OpenTelemetryConfiguration(OPEN_TELEMETRY).withEnvironment(environment::get);
    }

    private void start(SpoolRuntimeBuilder builder) {
        try {
            runtime = Optional.of(builder.build());
            runtime.get().start();
        } catch (RuntimeException e) {
            failure = Optional.of(e);
        }
    }

    private static String onAFreePort(String address) throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            URI given = URI.create(address);
            return given.getScheme() + "://localhost:" + socket.getLocalPort();
        }
    }
}
