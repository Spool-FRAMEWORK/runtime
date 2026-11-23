package software.spool.runtime.cluster;

import software.spool.core.adapter.logging.LoggerFactory;
import software.spool.core.model.spool.SpoolModule;
import software.spool.core.model.spool.SpoolNode;
import software.spool.core.port.logging.Logger;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Makes the node run what the Spool API assigns to it (ADR-031): starts the deployments it lacks, stops the ones it has
 * of more and restarts the ones that changed version, and tells the API how each one went. A deployment that failed
 * is not tried again until its version changes. Once the lease passes without an accepted heartbeat, it stops every
 * deployment.
 */
public final class AssignmentReconciler {
    private static final Logger LOG = LoggerFactory.getLogger(AssignmentReconciler.class);

    private final SpoolNode node;
    private final ClusterClient client;
    private final Function<String, List<SpoolModule>> modulesOf;
    private final Clock clock;
    private final Map<String, Integer> running = new HashMap<>();
    private final Map<String, Integer> failed = new HashMap<>();
    private Optional<Instant> leaseEnd = Optional.empty();

    public AssignmentReconciler(SpoolNode node, ClusterClient client, Function<String, List<SpoolModule>> modulesOf,
                                Clock clock) {
        this.node = node;
        this.client = client;
        this.modulesOf = modulesOf;
        this.clock = clock;
    }

    /** Runs what the API assigns, after a heartbeat it accepted. */
    public synchronized void reconcile(Assignments assignments) {
        leaseEnd = Optional.of(clock.instant().plus(assignments.lease()));
        Map<String, Integer> assigned = assignments.deployments().stream()
                .collect(Collectors.toMap(Assignment::name, Assignment::version));
        Set.copyOf(running.keySet()).stream()
                .filter(name -> !running.get(name).equals(assigned.get(name)))
                .forEach(this::stop);
        failed.keySet().removeIf(name -> !failed.get(name).equals(assigned.get(name)));
        assignments.deployments().stream()
                .filter(assignment -> !running.containsKey(assignment.name()) && !failed.containsKey(assignment.name()))
                .forEach(this::start);
    }

    /** Stops every deployment once the lease has passed since the last accepted heartbeat. */
    public synchronized void checkLease() {
        if (leaseEnd.filter(end -> clock.instant().isAfter(end)).isEmpty()) return;
        LOG.warn("No heartbeat accepted by the Spool API for longer than the lease; stopping {} deployments", running.size());
        Set.copyOf(running.keySet()).forEach(this::stop);
        leaseEnd = Optional.empty();
    }

    public synchronized void stopAll() {
        Set.copyOf(running.keySet()).forEach(this::stop);
    }

    private void start(Assignment assignment) {
        try {
            node.add(assignment.name(), modulesOf.apply(assignment.descriptor()));
            running.put(assignment.name(), assignment.version());
            LOG.info("Deployment {} in its version {} runs", assignment.name(), assignment.version());
            tell(() -> client.reportRunning(assignment.name(), assignment.version()));
        } catch (RuntimeException failure) {
            failed.put(assignment.name(), assignment.version());
            LOG.error("Deployment " + assignment.name() + " in its version " + assignment.version() + " could not start", failure);
            tell(() -> client.reportFailed(assignment.name(), assignment.version(), messageOf(failure)));
        }
    }

    private void stop(String name) {
        node.remove(name);
        running.remove(name);
        LOG.info("Deployment {} stopped", name);
    }

    private static void tell(Runnable report) {
        try {
            report.run();
        } catch (ClusterException e) {
            LOG.warn("Could not tell the Spool API how a deployment went: {}", e.getMessage());
        }
    }

    private static String messageOf(Throwable failure) {
        return Optional.ofNullable(failure.getMessage()).orElse(failure.getClass().getSimpleName());
    }
}
