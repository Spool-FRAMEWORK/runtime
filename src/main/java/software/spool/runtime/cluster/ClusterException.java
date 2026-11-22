package software.spool.runtime.cluster;

/** A call of the node to the Spool API that did not get the answer it needs. */
public final class ClusterException extends RuntimeException {
    public ClusterException(String message) {
        super(message);
    }

    public ClusterException(String message, Throwable cause) {
        super(message, cause);
    }
}
