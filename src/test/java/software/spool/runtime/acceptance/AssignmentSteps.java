package software.spool.runtime.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import software.spool.runtime.cluster.ClusterMembership;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

public class AssignmentSteps {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DESCRIPTOR = """
            infrastructure:
              bus: {kind: EVENT_BUS, type: IN_MEMORY}
              inbox: {kind: INBOX, type: %s, configuration: {path: "%s"}}
              lake: {kind: DATA_LAKE, type: FILE_SYSTEM, configuration: {path: "%s"}}
            modules:
              - ingester: {type: REACTIVE, id: %s}
            """;

    private final ClusterNodeUnderTest node;
    private final HttpClient client = HttpClient.newHttpClient();
    private final Map<String, Assigned> assigned = new LinkedHashMap<>();
    private int leaseSeconds = 30;

    public AssignmentSteps(ClusterNodeUnderTest node) {
        this.node = node;
    }

    @Given("the API assigns to {string} the deployment {string} in its version {int}, with a lease of {int} seconds")
    public void theApiAssigns(String nodeId, String deployment, int version, int lease) throws IOException {
        leaseSeconds = lease;
        assign(nodeId, deployment, version, "FILE_SYSTEM", deployment + "-ingester");
    }

    @Given("the API assigns to {string} the deployment {string} in its version {int}, whose inbox has the type {string}")
    public void theApiAssignsABrokenOne(String nodeId, String deployment, int version, String type) throws IOException {
        assign(nodeId, deployment, version, type, deployment + "-ingester");
    }

    @When("the API assigns to {string} the deployment {string} in its version {int}, whose module is {string}")
    public void theApiAssignsANewVersion(String nodeId, String deployment, int version, String module) throws IOException {
        assign(nodeId, deployment, version, "FILE_SYSTEM", module);
    }

    @When("the API assigns nothing to {string}")
    public void theApiAssignsNothing(String nodeId) throws IOException {
        assigned.clear();
        publish(nodeId);
    }

    @Given("the node started with a heartbeat every {int} second(s) and runs the module {string}")
    public void theNodeStartedAndRuns(int seconds, String module) throws Exception {
        node.environment.put(ClusterMembership.HEARTBEAT, seconds + "s");
        node.start(node.builder());
        theNodeRunsTheModule(10, module);
    }

    @When("{int} seconds pass")
    public void secondsPass(int seconds) throws InterruptedException {
        Thread.sleep(seconds * 1000L);
    }

    @When("the API stops answering")
    public void theApiStopsAnswering() {
        node.api.orElseThrow().stop();
    }

    @Then("within {int} seconds the node runs the module {string}")
    public void theNodeRunsTheModule(int seconds, String module) throws Exception {
        assertThat(node.failure).isEmpty();
        assertThat(within(seconds, health -> health.contains(quoted(module)))).as("the health lists " + module).isTrue();
    }

    @Then("within {int} seconds the node runs the module {string} and not {string}")
    public void theNodeRunsOnlyTheNew(int seconds, String module, String old) throws Exception {
        assertThat(within(seconds, health -> health.contains(quoted(module)) && !health.contains(quoted(old))))
                .as("the health lists " + module + " and not " + old).isTrue();
    }

    @Then("within {int} seconds the node runs no module")
    public void theNodeRunsNoModule(int seconds) throws Exception {
        assertThat(within(seconds, health -> health.contains("\"modules\":[]"))).as("the health lists no module").isTrue();
    }

    @Then("the API was told that {string} in its version {int} runs")
    public void theApiWasToldItRuns(String deployment, int version) throws Exception {
        assertThat(reportsWithin(10, deployment, "RUNNING", version)).isNotEmpty();
    }

    @Then("the API was told that {string} in its version {int} failed with an error that says {string}")
    public void theApiWasToldItFailed(String deployment, int version, String text) throws Exception {
        assertThat(reportsWithin(10, deployment, "FAILED", version)).anySatisfy(body -> assertThat(body).contains(text));
    }

    @Then("the API was told only once that {string} in its version {int} failed")
    public void theApiWasToldOnce(String deployment, int version) {
        assertThat(reports(deployment, "FAILED", version)).hasSize(1);
    }

    private void assign(String nodeId, String deployment, int version, String inboxType, String module) throws IOException {
        assigned.put(deployment, new Assigned(deployment, version, descriptor(deployment, inboxType, module)));
        publish(nodeId);
    }

    private void publish(String nodeId) throws IOException {
        node.api.orElseThrow().assign(nodeId, JSON.writeValueAsString(
                Map.of("leaseSeconds", leaseSeconds, "deployments", new ArrayList<>(assigned.values()))));
    }

    private String descriptor(String deployment, String inboxType, String module) throws IOException {
        Path folder = Files.createTempDirectory("spool-" + deployment);
        return DESCRIPTOR.formatted(inboxType, slashed(folder.resolve("inbox")), slashed(folder.resolve("lake")), module);
    }

    private boolean within(int seconds, Predicate<String> health) throws Exception {
        Instant deadline = Instant.now().plusSeconds(seconds);
        while (Instant.now().isBefore(deadline)) {
            if (health.test(health())) return true;
            Thread.sleep(100);
        }
        return health.test(health());
    }

    private String health() throws Exception {
        URI address = URI.create(node.environment.get(ClusterMembership.NODE_ADDRESS));
        return client.send(HttpRequest.newBuilder(address.resolve("/spool/health")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    private List<String> reportsWithin(int seconds, String deployment, String state, int version) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(seconds);
        while (reports(deployment, state, version).isEmpty() && Instant.now().isBefore(deadline)) Thread.sleep(100);
        return reports(deployment, state, version);
    }

    private List<String> reports(String deployment, String state, int version) {
        return node.api.orElseThrow().received().stream()
                .filter(request -> request.method().equals("PUT"))
                .filter(request -> request.path().endsWith("/assignments/" + deployment))
                .filter(RecordingSpoolApi.Received::signed)
                .map(RecordingSpoolApi.Received::body)
                .filter(body -> body.contains("\"state\":\"" + state + "\"") && body.contains("\"version\":" + version))
                .toList();
    }

    private static String quoted(String text) {
        return "\"" + text + "\"";
    }

    private static String slashed(Path path) {
        return path.toString().replace('\\', '/');
    }

    private record Assigned(String name, int version, String descriptor) {
    }
}
