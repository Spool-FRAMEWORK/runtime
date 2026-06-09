package software.spool.runtime;

import org.junit.jupiter.api.Test;
import software.spool.core.model.spool.SpoolNode;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;

class SpoolRuntimeTest {

    @Test
    void start_withEmptyNodeList_doesNotThrow() {
        SpoolRuntime runtime = new SpoolRuntime(List.of());
        assertThatNoException().isThrownBy(runtime::start);
    }

    @Test
    void start_withSingleNode_startsWithoutException() throws IOException {
        int port = freePort();
        SpoolNode node = SpoolNode.create(port);
        SpoolRuntime runtime = new SpoolRuntime(List.of(node));

        runtime.start();

        node.stop();
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
