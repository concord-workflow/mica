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
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DryRunTest {

    private HttpServer server;
    private AtomicInteger listEntitiesCount;
    private String baseUrl;

    @BeforeEach
    public void setUp() throws Exception {
        listEntitiesCount = new AtomicInteger(0);

        var port = -1;

        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        baseUrl = "http://localhost:" + port;

        server.createContext("/api/mica/v1/entity", exchange -> {
            listEntitiesCount.incrementAndGet();

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
    public void batchDeleteShouldBeSkipped(@TempDir Path workDir) throws Exception {
        var ctx = new MockContext(baseUrl, workDir);
        var task = new MicaTask(new ObjectMapper(), ctx);
        var input = new MapBackedVariables(Map.of(
                "action", "batch",
                "operation", "delete",
                "namePatterns", List.of("/test/batchDeleteShouldBeSkipped/.*"),
                "dryRun", true));

        var output = (TaskResult.SimpleResult) task.execute(input);
        assertEquals(List.of(), output.values().get("deletedEntities"));

        // here we don't expect any API calls in dry-run mode
        assertEquals(0, listEntitiesCount.get());
    }

    @Test
    public void listEntitiesShouldReturnActualData(@TempDir Path workDir) throws Exception {
        var ctx = new MockContext(baseUrl, workDir);
        var task = new MicaTask(new ObjectMapper(), ctx);
        var input = new MapBackedVariables(Map.of(
                "action", "listEntities",
                "dryRun", true));

        var output = (TaskResult.SimpleResult) task.execute(input);
        assertEquals(List.of(), output.values().get("data"));

        // just one call to /api/mica/v1/entity
        assertEquals(1, listEntitiesCount.get());
    }

    @Test
    public void uploadShouldBeSkipped(@TempDir Path workDir) throws Exception {
        var src = workDir.resolve("test.txt");
        Files.writeString(src, "hello: 'world'");

        var ctx = new MockContext(baseUrl, workDir);
        var task = new MicaTask(new ObjectMapper(), ctx);
        var input = new MapBackedVariables(Map.of(
                "action", "upload",
                "kind", "/test/v1",
                "src", src.toAbsolutePath().toString(),
                "name", "/test/uploadShouldBeSkipped",
                "dryRun", true));

        var output = (TaskResult.SimpleResult) task.execute(input);
        assertNotNull(output.values().get("version"));

        // here we don't expect any API calls in dry-run mode
        assertEquals(0, listEntitiesCount.get());
    }

    @Test
    public void upsertShouldBeSkipped(@TempDir Path workDir) throws Exception {
        var ctx = new MockContext(baseUrl, workDir);
        var task = new MicaTask(new ObjectMapper(), ctx);
        var input = new MapBackedVariables(Map.of(
                "action", "upsert",
                "kind", "/test/v1",
                "entity", Map.of("hello", "world"),
                "name", "/test/upsertShouldBeSkipped",
                "dryRun", true));

        var output = (TaskResult.SimpleResult) task.execute(input);
        assertNotNull(output.values().get("version"));

        // in dry-run mode we expect one call to /api/mica/v1/entity
        // (to return an existing version)
        assertEquals(1, listEntitiesCount.get());
    }
}
