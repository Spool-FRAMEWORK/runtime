Feature: The runtime starts from a descriptor in the file system

  The same jar can be deployed with different descriptors: the runtime reads the descriptor from a path of the
  file system, and a resource path still reads it from the classpath.

  @DEV-84
  Scenario: The runtime starts from a descriptor at an absolute path
    Given a valid descriptor "Spool.yaml" in a folder outside the classpath
    When the runtime is built from the absolute path of "Spool.yaml"
    Then the runtime is built

  @DEV-84
  Scenario: The runtime reads that very file
    Given a descriptor "Spool.yaml" in a folder outside the classpath with a crawler without source
    When the runtime is built from the absolute path of "Spool.yaml"
    Then building fails saying "modules[0].crawler.source is required"
    And the error names the path of "Spool.yaml"

  @DEV-84
  Scenario: A path that does not exist is named in the error
    When the runtime is built from the absolute path of "missing.yaml"
    Then building fails saying "Descriptor not found in the file system: "
    And the error names the path of "missing.yaml"

  @DEV-84
  Scenario: A descriptor in the classpath is still read from its resource path
    When the runtime is built from the classpath resource "/descriptors/crawler-without-source.yaml"
    Then building fails saying "modules[0].crawler.source is required"
