Feature: The runtime starts one node per descriptor of a folder

  A folder of descriptors becomes one node per .spool.yaml file, so adding a pipeline does not mean touching the
  code. Two descriptors given to the same runtime cannot use the same port, wherever each one comes from.

  @DEV-24
  Scenario: The runtime starts a node for each descriptor of a folder
    Given a folder outside the classpath with the descriptors "boe.spool.yaml" on port 8091 and "bocm.spool.yaml" on port 8092
    When the runtime is built from that folder
    Then the runtime has 2 nodes

  @DEV-24
  Scenario: A descriptor added on its own and one of a folder cannot share a port
    Given a folder outside the classpath with the descriptors "boe.spool.yaml" on port 8091 and "bocm.spool.yaml" on port 8092
    And a descriptor "Spool.yaml" on port 8091 in a folder outside the classpath
    When the runtime is built from that folder and from the absolute path of "Spool.yaml"
    Then building fails saying "both open port 8091: give each node its own server.port"
