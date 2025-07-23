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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(60)
public class ConnectionTimeoutTest {

    private ServerSocket serverSocket;
    private int port;

    @BeforeEach
    public void setUp() throws Exception {
        serverSocket = new ServerSocket(0);
        port = serverSocket.getLocalPort();

        var serverThread = new Thread(() -> {
            try (var ignored = serverSocket.accept()) {
                Thread.sleep(10000);
            } catch (Exception ignored) {
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @AfterEach
    public void tearDown() throws IOException {
        serverSocket.close();
    }

    @Test
    public void handleConnectionTimeout() {
        var baseUri = URI.create("http://localhost:" + port);
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        var micaClient = new MicaClient(httpClient, baseUri, requesBuilder -> requesBuilder, "test", new ObjectMapper(),
                Duration.ofSeconds(2));
        var req = new MicaClient.ListEntitiesParameters(null, null, null, null, null, 1);
        assertThrows(ClientException.class, () -> micaClient.listEntities(req));
    }
}
