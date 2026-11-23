package software.spool.runtime.cluster;

import java.time.Duration;
import java.util.List;

/**
 * What the Spool API assigns to the node, and for how long the node may keep running it without the API accepting a
 * heartbeat: its lease.
 */
public record Assignments(Duration lease, List<Assignment> deployments) {
    public Assignments {
        deployments = List.copyOf(deployments);
    }
}
