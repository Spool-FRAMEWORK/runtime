package software.spool.runtime.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import software.spool.runtime.cluster.ClusterMembership;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

public class ClusterSteps {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ClusterNodeUnderTest node;

    public ClusterSteps(ClusterNodeUnderTest node) {
        this.node = node;
    }

    @Given("a Spool API that records what it receives")
    public void aRecordingApi() throws IOException {
        node.startApi();
    }

    @Given("the environment has the cluster variables for {string} at {string}")
    public void theClusterVariables(String nodeId, String address) throws IOException {
        node.environment.put(ClusterMembership.API,
                node.api.map(RecordingSpoolApi::address).orElse(URI.create("http://localhost:1")).toString());
        node.environment.put(ClusterMembership.NODE_ID, nodeId);
        node.environment.put(ClusterMembership.NODE_ADDRESS, onAFreePort(address));
        node.environment.put(ClusterMembership.SECRET, ClusterNodeUnderTest.SECRET);
    }

    @Given("the environment has the cluster variables for {string} at {string} but not SPOOL_CLUSTER_SECRET")
    public void theClusterVariablesButTheSecret(String nodeId, String address) throws IOException {
        theClusterVariables(nodeId, address);
        node.environment.remove(ClusterMembership.SECRET);
    }

    @Given("the environment has SPOOL_NODE_LABELS set to {string}")
    public void theNodeLabels(String labels) {
        node.environment.put(ClusterMembership.LABELS, labels);
    }

    @Then("the API received the registration of {string} with the labels {string}")
    public void theApiReceivedTheRegistrationWithTheLabels(String nodeId, String labels) throws IOException {
        assertThat(labelsOfTheRegistrationOf(nodeId)).containsExactly(labels.split(","));
    }

    @Then("the API received the registration of {string} with no labels")
    public void theApiReceivedTheRegistrationWithNoLabels(String nodeId) throws IOException {
        assertThat(labelsOfTheRegistrationOf(nodeId)).isEmpty();
    }

    @Given("the environment has no cluster variables")
    public void noClusterVariables() {
        node.environment.clear();
    }

    @When("the node starts with a heartbeat every {int} second(s)")
    public void theNodeStartsWithAHeartbeat(int seconds) {
        node.environment.put(ClusterMembership.HEARTBEAT, seconds + "s");
        node.start(node.builder());
    }

    @When("the node starts from the descriptor {string}")
    public void theNodeStartsFromTheDescriptor(String resource) {
        node.start(node.builder().withNodeFromDSL("/" + resource));
    }

    @When("the node starts")
    public void theNodeStarts() {
        node.start(node.builder());
    }

    @Then("the API received the registration of {string} at {string}, signed with the secret")
    public void theApiReceivedTheRegistration(String nodeId, String address) {
        assertThat(node.failure).isEmpty();
        assertThat(node.api.orElseThrow().received()).filteredOn(request -> request.method().equals("POST"))
                .singleElement().satisfies(request -> {
                    assertThat(request.path()).isEqualTo("/spool/cluster/nodes");
                    assertThat(request.body()).contains("\"id\":\"" + nodeId + "\"")
                            .contains(node.environment.get(ClusterMembership.NODE_ADDRESS));
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
        assertThat(node.failure).isEmpty();
        assertThat(node.runtime.orElseThrow().nodes()).hasSize(1);
    }

    @Then("the API received nothing")
    public void theApiReceivedNothing() {
        assertThat(node.api.orElseThrow().received()).isEmpty();
    }

    @Then("the start fails with an error that says {string}")
    public void theStartFails(String text) {
        assertThat(node.failure).hasValueSatisfying(error -> assertThat(error).hasMessageContaining(text));
    }

    private List<String> labelsOfTheRegistrationOf(String nodeId) throws IOException {
        assertThat(node.failure).isEmpty();
        RecordingSpoolApi.Received registration = node.api.orElseThrow().received().stream()
                .filter(request -> request.method().equals("POST") && request.path().equals("/spool/cluster/nodes"))
                .filter(request -> request.body().contains("\"id\":\"" + nodeId + "\""))
                .findFirst().orElseThrow();
        JsonNode labels = JSON.readTree(registration.body()).path("labels");
        return StreamSupport.stream(labels.spliterator(), false).map(JsonNode::asText).toList();
    }

    private long heartbeatsOf(String nodeId) {
        return node.api.orElseThrow().received().stream()
                .filter(request -> request.method().equals("PUT"))
                .filter(request -> request.path().equals("/spool/cluster/nodes/" + nodeId + "/heartbeat"))
                .filter(RecordingSpoolApi.Received::signed)
                .count();
    }

    private static String onAFreePort(String address) throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            URI given = URI.create(address);
            return given.getScheme() + "://localhost:" + socket.getLocalPort();
        }
    }
}
