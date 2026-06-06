# Spool — Runtime

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/build-Maven-C71A36.svg)](https://maven.apache.org/)

**Runtime** is the **Spool** framework module that **bootstraps and starts `SpoolNode` instances** — either built manually in code or loaded from a `Spool.yaml` descriptor. It also configures the OpenTelemetry pipeline for the whole process.

---

## Table of Contents

- [What it does](#what-it-does)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Examples](#examples)
  - [Boot from YAML descriptor](#boot-from-yaml-descriptor)
  - [Boot from code](#boot-from-code)
  - [Mixed — YAML + manual node](#mixed--yaml--manual-node)
- [OpenTelemetry Configuration](#opentelemetry-configuration)
- [License](#license)

---

## What it does

```
SpoolRuntimeBuilder
      │
      ├── withNodeFromDSL("/Spool.yaml")  →  SpoolNodeDSL.fromDescriptor(path)
      │                                          │
      │                                          ▼
      ├── withNode(SpoolNode)             →  SpoolNode (manually built)
      │
      └── build() → SpoolRuntime
                         │
                         ▼
                    SpoolRuntime.start()
                    (starts all registered SpoolNodes)
```

`SpoolRuntime` is the entry point for production applications — it handles OTEL initialisation before any node starts so metrics, traces, and logs flow from the first event.

---

## Installation

```xml
<dependency>
    <groupId>io.github.spool-framework</groupId>
    <artifactId>runtime</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/Spool-FRAMEWORK/*</url>
    </repository>
</repositories>
```

> Requires **Java 21+**.

---

## Quick Start

```java
public class Main {
    public static void main(String[] args) {
        SpoolRuntime.builder()
                .OpenTelemetryConfiguration(new OpenTelemetryConfiguration(
                        "my-service",
                        "http://otel-collector:4318/v1/traces",
                        "http://loki:3100/otlp/v1/logs",
                        "http://otel-collector:4318/v1/metrics"
                ))
                .withNodeFromDSL("/Spool.yaml")
                .build()
                .start();
    }
}
```

---

## Examples

### Boot from YAML descriptor

The simplest setup — describe everything in `Spool.yaml` and boot with one call.

```java
SpoolRuntime.builder()
        .OpenTelemetryConfiguration(otelConfig)
        .withNodeFromDSL("/Spool.yaml")
        .build()
        .start();
```

---

### Boot from code

Build `SpoolNode` instances manually and pass them to the runtime.

```java
InMemoryEventBus bus = new InMemoryEventBus();

Crawler crawler = CrawlerBuilderFactory
        .watchdog("http://spool-watchdog:8080", "my-crawler")
        .poll(new HTTPPollSource("https://api.example.com/data", "my-api"))
        .source()
            .ports(CrawlerPorts.builder().inbox(inboxWriter).bus(bus).build())
            .schedule(PollingConfiguration.every(Duration.ofMinutes(1)))
            .and()
        .createWith(StandardNormalizer.JSON_ARRAY);

SpoolNode node = SpoolNode.create()
        .register(crawler);

SpoolRuntime.builder()
        .OpenTelemetryConfiguration(otelConfig)
        .withNode(node)
        .build()
        .start();
```

---

### Mixed — YAML + manual node

Combine declarative and programmatic nodes in the same runtime.

```java
SpoolRuntime.builder()
        .OpenTelemetryConfiguration(otelConfig)
        .withNodeFromDSL("/Spool.yaml")       // declared pipeline
        .withNode(customNode)                  // manually built node
        .build()
        .start();
```

---

## OpenTelemetry Configuration

`OpenTelemetryConfiguration` sets up traces, logs, and metrics for the entire process.

```java
new OpenTelemetryConfiguration(
    "my-service-name",            // SERVICE_NAME in Grafana/Tempo
    "http://otel-collector:4318/v1/traces",
    "http://loki:3100/otlp/v1/logs",
    "http://otel-collector:4318/v1/metrics"
)
```

The `.docker` folder in this module contains a ready-to-run `docker-compose.yml` with Grafana, Prometheus, Loki, Tempo, and the OTel Collector pre-configured.

---

## License

Distributed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
