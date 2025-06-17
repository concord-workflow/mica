package ca.ibodrov.mica.concord.task;

/*-
 * ~~~~~~
 * Mica
 * ------
 * Copyright (C) 2023 - 2025 Mica Authors
 * ------
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ======
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.walmartlabs.concord.runtime.v2.runner.SensitiveDataHolder;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    public void retryOnBadStatus(@TempDir Path workDir) throws Exception {
        var baseUrl = "http://localhost:" + port;

        var ctx = new MockContext(baseUrl, workDir);
        var task = new MicaTask(new ObjectMapper(), new SensitiveDataHolder(), ctx);
        var input = new MapBackedVariables(Map.of("action", "listEntities"));

        var output = (TaskResult.SimpleResult) task.execute(input);
        assertEquals(List.of(), output.values().get("data"));

        assertEquals(3, requestCount.get());
    }

    @Test
    public void doNotRetryOnInvalidHost(@TempDir Path workDir) {
        var baseUrl = "http://test" + System.currentTimeMillis() + ".localdomain:12345";
        var ctx = new MockContext(baseUrl, workDir);
        var task = new MicaTask(new ObjectMapper(), new SensitiveDataHolder(), ctx);
        var input = new MapBackedVariables(Map.of("action", "listEntities"));
        assertThrows(ClientException.class, () -> task.execute(input));
    }
}
