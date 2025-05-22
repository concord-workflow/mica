package ca.ibodrov.mica.standalone;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        SLF4JBridgeHandler.install();

        var cfg = new Configuration()
                .configureServerPortUsingEnv()
                .configureSecretsUsingEnv()
                .configureOidcUsingEnv()
                .configureDatabaseUsingEnv()
                .configureDataDirUsingEnv()
                .toMap();

        try (var server = new MicaServer(cfg)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Received SIGTERM, stopping the server...");
                try {
                    server.stop();
                } catch (Exception e) {
                    log.warn("Failed to stop the server graciously: {}", e.getMessage());
                }
            }, "shutdown-hook"));

            server.start();

            log.info("""

                    MICA is ready.

                    """);

            server.waitForStop();
        }

        log.info("Bye!");
    }
}
