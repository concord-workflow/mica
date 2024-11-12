package ca.ibodrov.mica.concord.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RetryTest {

    private HttpServer server;
    private AtomicInteger requestCount;
    private int port;

    @BeforeEach
    public void setUp() throws Exception {
        requestCount = new AtomicInteger(0);

        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", exchange -> {
            var count = requestCount.incrementAndGet();

            if (count < 3) {
                exchange.sendResponseHeaders(500, 0);
                exchange.close();
                return;
            }

            var response = "{\"data\": []}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
    }

    @AfterEach
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void test() throws Exception {
        var baseUrl = "http://localhost:" + port;

        var ctx = new MockContext(baseUrl);
        var task = new MicaTask(new ObjectMapper(), ctx);
        var input = new MapBackedVariables(Map.of("action", "listEntities"));

        var output = (TaskResult.SimpleResult) task.execute(input);
        assertEquals(List.of(), output.values().get("data"));

        assertEquals(3, requestCount.get());
    }

}
