package software.spool.runtime.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.spool.core.model.Event;
import software.spool.core.model.spool.SpoolNode;
import software.spool.core.model.vo.Envelope;
import software.spool.core.model.vo.MediaType;
import software.spool.core.pipeline.Pipeline;
import software.spool.core.port.watchdog.ModuleHeartBeat;
import software.spool.crawler.api.Crawler;
import software.spool.crawler.api.builder.StreamCrawlerBuilder;
import software.spool.crawler.api.utils.CrawlerPorts;
import software.spool.crawler.internal.utils.factory.Normalizer;
import software.spool.infrastructure.adapter.bus.memory.InMemoryEventBus;
import software.spool.infrastructure.adapter.pollsource.stream.InMemoryStreamSource;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SpoolNodeE2ETest {

    private SpoolNode node;
    private InMemoryEventBus bus;
    private List<Envelope> captured;

    @BeforeEach
    void setUp() throws IOException {
        bus = new InMemoryEventBus();
        captured = new ArrayList<>();

        InMemoryStreamSource<TestPayloadEvent> source = new InMemoryStreamSource<>(bus, TestPayloadEvent.class, "e2e-source");

        CrawlerPorts ports = CrawlerPorts.builder()
            .inbox(env -> { captured.add(env); return env.idempotencyKey(); })
            .bus(bus)
            .build();

        Crawler crawler = new StreamCrawlerBuilder<TestPayloadEvent>(source, ModuleHeartBeat.NOOP)
            .source()
                .ports(ports)
                .mediaType(MediaType.of("application/json"))
                .and()
            .observability()
                .withMetrics(software.spool.core.port.metrics.MetricsRegistry.NOOP)
                .and()
            .createWith(new Normalizer<>(
                Pipeline.<TestPayloadEvent>start()
                    .add(e -> Stream.of(("{\"id\":\"" + e.id() + "\"}").getBytes()))
            ));

        node = SpoolNode.create(freePort());
        node.register(crawler);
        node.start();
    }

    @AfterEach
    void tearDown() {
        node.stop();
    }

    @Test
    void e2e_singleEvent_reachesInbox() {
        bus.publish(new TestPayloadEvent("evt-1"));

        assertThat(captured).hasSize(1);
    }

    @Test
    void e2e_multipleEvents_allReachInbox() {
        bus.publish(new TestPayloadEvent("evt-1"));
        bus.publish(new TestPayloadEvent("evt-2"));
        bus.publish(new TestPayloadEvent("evt-3"));

        assertThat(captured).hasSize(3);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    record TestPayloadEvent(String id) implements Event {
        @Override public String eventId() { return id; }
        @Override public String causationId() { return null; }
        @Override public String correlationId() { return id; }
        @Override public Instant timestamp() { return Instant.now(); }
    }
}
