package ca.ibodrov.mica.standalone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.security.SecureRandom;
import java.util.Base64;

public class ExampleMicaServer {

    private static final Logger log = LoggerFactory.getLogger(ExampleMicaServer.class);

    private static final String ADMIN_API_TOKEN = "mica";

    public static void main(String[] args) throws Exception {
        var authServerUri = assertEnv("MICA_AUTH_SERVER_URI");
        var oidcClientId = assertEnv("MICA_OIDC_CLIENT_ID");
        var oidcSecret = assertEnv("MICA_OIDC_SECRET");

        try (var db = new PostgreSQLContainer<>("postgres:15-alpine")) {
            db.start();

            var cfg = new Configuration().configureServerPortUsingEnv()
                    .configureSecrets(randomString(64))
                    .configureOidc("http://localhost:8080", authServerUri, oidcClientId, oidcSecret)
                    .configureDatabase(db.getJdbcUrl(), db.getUsername(), db.getPassword(), ADMIN_API_TOKEN, true)
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
                        ==============================================================

                          UI: http://localhost:8080/mica/
                          DB:
                            JDBC URL: {}
                            username: {}
                            password: {}
                        """, db.getJdbcUrl(),
                        db.getUsername(),
                        db.getPassword());

                Thread.currentThread().join();
            }
        }
    }

    private static String randomString(int minLength) {
        byte[] ab = new byte[minLength];
        new SecureRandom().nextBytes(ab);
        return Base64.getEncoder().encodeToString(ab);
    }

    private static String assertEnv(String key) {
        var v = System.getenv(key);
        if (v == null) {
            throw new RuntimeException("Missing %s environment variable".formatted(key));
        }
        return v;
    }
}
