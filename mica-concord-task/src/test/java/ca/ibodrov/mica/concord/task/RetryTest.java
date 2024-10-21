package ca.ibodrov.mica.concord.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.walmartlabs.concord.runtime.v2.sdk.Compiler;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    private static final class MockContext implements Context {

        private final String baseUrl;

        private MockContext(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        @Override
        public Path workingDirectory() {
            return null;
        }

        @Override
        public UUID processInstanceId() {
            return null;
        }

        @Override
        public Variables variables() {
            return null;
        }

        @Override
        public Variables defaultVariables() {
            return new MapBackedVariables(Map.of());
        }

        @Override
        public FileService fileService() {
            return null;
        }

        @Override
        public DockerService dockerService() {
            return null;
        }

        @Override
        public SecretService secretService() {
            return null;
        }

        @Override
        public LockService lockService() {
            return null;
        }

        @Override
        public ApiConfiguration apiConfiguration() {
            return new ApiConfiguration() {
                @Override
                public String baseUrl() {
                    return baseUrl;
                }

                @Override
                public int connectTimeout() {
                    return 5000;
                }

                @Override
                public int readTimeout() {
                    return 5000;
                }
            };
        }

        @Override
        public ProcessConfiguration processConfiguration() {
            return new ProcessConfiguration() {
                @Nullable
                @Override
                public UUID instanceId() {
                    return null;
                }

                @Override
                public Map<String, Object> initiator() {
                    return Map.of();
                }

                @Override
                public Map<String, Object> currentUser() {
                    return Map.of();
                }

                @Override
                public ProcessInfo processInfo() {
                    return new ProcessInfo() {
                        @Override
                        public String sessionToken() {
                            return "foobar";
                        }
                    };
                }
            };
        }

        @Override
        public Execution execution() {
            return null;
        }

        @Override
        public Compiler compiler() {
            return null;
        }

        @Override
        public <T> T eval(Object v, Class<T> type) {
            return null;
        }

        @Override
        public <T> T eval(Object v, Map<String, Object> additionalVariables, Class<T> type) {
            return null;
        }

        @Override
        public void suspend(String eventName) {

        }

        @Override
        public void reentrantSuspend(String eventName, Map<String, Serializable> payload) {

        }
    }
}
