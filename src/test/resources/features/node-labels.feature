Feature: A node in cluster mode registers with the labels of SPOOL_NODE_LABELS

  A node in cluster mode declares where it runs with labels, in SPOOL_NODE_LABELS as key=value pairs separated by
  commas, and sends them to the Spool API when it registers (ADR-036). Without the variable it registers with no
  labels.

  @DEV-143
  Scenario: A node registers with the labels of SPOOL_NODE_LABELS
    Given a Spool API that records what it receives
    And the environment has the cluster variables for "node-a" at "http://10.0.0.1:8080"
    And the environment has SPOOL_NODE_LABELS set to "cloud=aws,region=eu-west-1"
    When the node starts with a heartbeat every 1 second
    Then the API received the registration of "node-a" with the labels "cloud=aws,region=eu-west-1"

  @DEV-143
  Scenario: A node without SPOOL_NODE_LABELS registers with no labels
    Given a Spool API that records what it receives
    And the environment has the cluster variables for "node-a" at "http://10.0.0.1:8080"
    When the node starts with a heartbeat every 1 second
    Then the API received the registration of "node-a" with no labels

  @DEV-143
  Scenario: A label not written as key=value stops the start, naming the variable
    Given the environment has the cluster variables for "node-a" at "http://10.0.0.1:8080"
    And the environment has SPOOL_NODE_LABELS set to "cloud=aws,eu-west-1"
    When the node starts
    Then the start fails with an error that says "SPOOL_NODE_LABELS"
