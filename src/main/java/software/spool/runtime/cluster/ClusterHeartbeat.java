package software.spool.runtime.cluster;

import software.spool.core.adapter.logging.LoggerFactory;
import software.spool.core.model.schedule.Schedule;
import software.spool.core.port.logging.Logger;
import software.spool.core.utils.polling.CancellationToken;
import software.spool.core.utils.polling.PollingConfiguration;

/**
 * Registers the node in the Spool API when it starts and then says it is alive every interval. A heartbeat that
 * fails is logged and the next one is tried: the API marks the node down only after its timeout.
 */
public final class ClusterHeartbeat {
    private static final Logger LOG = LoggerFactory.getLogger(ClusterHeartbeat.class);

    private final ClusterClient client;
    private final PollingConfiguration polling;
    private CancellationToken token = CancellationToken.NOOP;

    public ClusterHeartbeat(ClusterClient client, ClusterMembership membership) {
        this.client = client;
        this.polling = PollingConfiguration.of(Schedule.every(membership.heartbeatInterval()));
    }

    public void start() {
        client.register();
        token = CancellationToken.create();
        polling.scheduler().schedule(this::beat, polling.schedule(), token);
    }

    public void stop() {
        token.cancel();
    }

    private void beat() {
        try {
            client.beat();
        } catch (ClusterException e) {
            LOG.warn("Heartbeat to the Spool API failed; trying again in the next one: {}", e.getMessage());
        }
    }
}
