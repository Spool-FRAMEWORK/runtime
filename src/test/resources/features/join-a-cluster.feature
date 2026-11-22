Feature: A node started in cluster mode joins the Spool API instead of loading a descriptor

  With SPOOL_CLUSTER_API, SPOOL_NODE_ID, SPOOL_NODE_ADDRESS and SPOOL_CLUSTER_SECRET, a node starts without a
  descriptor, registers in the Spool API and sends it heartbeats, every request signed with the secret (ADR-022).
  Without them, it starts from its descriptor as before.

  @DEV-107
  Scenario: A node in cluster mode registers and sends heartbeats
    Given a Spool API that records what it receives
    And the environment has the cluster variables for "node-a" at "http://10.0.0.1:8080"
    When the node starts with a heartbeat every 1 second
    Then the API received the registration of "node-a" at "http://10.0.0.1:8080", signed with the secret
    And within 3 seconds the API received at least 2 heartbeats of "node-a", signed with the secret

  @DEV-107
  Scenario: A node without the cluster variables starts from its descriptor and does not call any API
    Given a Spool API that records what it receives
    And the environment has no cluster variables
    When the node starts from the descriptor "descriptors/minimal.spool.yaml"
    Then the node runs the modules of the descriptor
    And the API received nothing

  @DEV-107
  Scenario: Cluster mode without one of its variables stops the start, naming it
    Given the environment has the cluster variables for "node-a" at "http://10.0.0.1:8080" but not SPOOL_CLUSTER_SECRET
    When the node starts
    Then the start fails with an error that says "SPOOL_CLUSTER_SECRET"
