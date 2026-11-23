package software.spool.runtime.cluster;

/** A deployment the Spool API assigns to the node: its name, its version and its descriptor (ADR-031). */
public record Assignment(String name, int version, String descriptor) {
}
