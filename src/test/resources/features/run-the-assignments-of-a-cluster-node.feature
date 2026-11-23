Feature: A node in cluster mode runs what the Spool API assigns to it, and stops it when it loses the API

  With each heartbeat, the node asks the API for its assignments. It starts the deployments it lacks, stops the ones
  it has of more and restarts the ones that changed version, and tells the API how each one went. Once the lease the
  API gave it passes without an accepted heartbeat, it stops every deployment, so the same one does not run on two
  nodes when the API moves it (ADR-031).

  Background:
    Given a Spool API that records what it receives
    And the environment has the cluster variables for "node-a" at "http://10.0.0.1:8080"

  @DEV-94
  Scenario: An assigned deployment starts in the node, which says it runs
    Given the API assigns to "node-a" the deployment "orders" in its version 1, with a lease of 30 seconds
    When the node starts with a heartbeat every 1 second
    Then within 10 seconds the node runs the module "orders-ingester"
    And the API was told that "orders" in its version 1 runs

  @DEV-94
  Scenario: A deployment that is no longer assigned stops
    Given the API assigns to "node-a" the deployment "orders" in its version 1, with a lease of 30 seconds
    And the node started with a heartbeat every 1 second and runs the module "orders-ingester"
    When the API assigns nothing to "node-a"
    Then within 10 seconds the node runs no module

  @DEV-94
  Scenario: A new version replaces the one that runs
    Given the API assigns to "node-a" the deployment "orders" in its version 1, with a lease of 30 seconds
    And the node started with a heartbeat every 1 second and runs the module "orders-ingester"
    When the API assigns to "node-a" the deployment "orders" in its version 2, whose module is "orders-ingester-2"
    Then within 10 seconds the node runs the module "orders-ingester-2" and not "orders-ingester"
    And the API was told that "orders" in its version 2 runs

  @DEV-94
  Scenario: A deployment that cannot start is reported with its error, and the node keeps running the others
    Given the API assigns to "node-a" the deployment "orders" in its version 1, with a lease of 30 seconds
    And the API assigns to "node-a" the deployment "broken" in its version 1, whose inbox has the type "NOBODY_HAS_THIS"
    When the node starts with a heartbeat every 1 second
    Then within 10 seconds the node runs the module "orders-ingester"
    And the API was told that "broken" in its version 1 failed with an error that says "NOBODY_HAS_THIS"

  @DEV-94
  Scenario: A deployment that failed is not tried again until its version changes
    Given the API assigns to "node-a" the deployment "broken" in its version 1, whose inbox has the type "NOBODY_HAS_THIS"
    When the node starts with a heartbeat every 1 second
    And 12 seconds pass
    Then the API was told only once that "broken" in its version 1 failed

  @DEV-94
  Scenario: A node that loses the API for longer than its lease stops every deployment
    Given the API assigns to "node-a" the deployment "orders" in its version 1, with a lease of 2 seconds
    And the node started with a heartbeat every 1 second and runs the module "orders-ingester"
    When the API stops answering
    Then within 5 seconds the node runs no module
