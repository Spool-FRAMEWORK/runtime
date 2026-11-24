package software.spool.runtime.cluster;

import java.util.Map;

/**
 * A deployment the Spool API assigns to the node (ADR-031): its name, its version, and its files by their path, with
 * the main one among them (ADR-036).
 */
public record Assignment(String name, int version, String main, Map<String, String> files) {
    public Assignment {
        files = Map.copyOf(files);
    }
}
