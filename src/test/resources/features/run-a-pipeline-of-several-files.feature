Feature: A node in cluster mode runs a pipeline of several files, with its includes resolved

  The Spool API gives the node a deployment as its main file and the files it includes. The node loads them as it would
  from a folder, so an include names a file by its path relative to the file that includes it (ADR-036).

  Background:
    Given a Spool API that records what it receives
    And the environment has the cluster variables for "node-a" at "http://10.0.0.1:8080"

  @DEV-145
  Scenario: A deployment whose infrastructure is in an included file starts
    Given the API assigns to "node-a" the deployment "orders" whose main file includes its infrastructure from "shared/infrastructure.yaml"
    When the node starts with a heartbeat every 1 second
    Then within 10 seconds the node runs the module "orders-ingester"
    And the API was told that "orders" in its version 1 runs
