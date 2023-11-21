package ca.ibodrov.mica.its;

import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.Optional;

/**
 * Basic concord-server plus Mica. Run it in your IDE.
 */
public class LocalMicaServer {

    public static void main(String[] args) throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:15-alpine");
                var server = new TestingMicaServer(db, 8001, createConfig())) {
            db.start();
            server.start();
            System.out.printf("""
                    ==============================================================

                      UI (hosted): http://localhost:8001/mica/
                      UI (dev): http://localhost:5173/mica/ (launched separately)
                      DB:
                        JDBC URL: %s
                        username: %s
                        password: %s

                    ==============================================================
                    %n""", db.getJdbcUrl(), db.getUsername(), db.getPassword());
            Thread.currentThread().join();
        }
    }

    private static String assertEnvVar(String key) {
        return Optional.ofNullable(System.getenv(key))
                .orElseThrow(() -> new RuntimeException("Missing %s env var".formatted(key)));
    }

    private static Map<String, String> createConfig() {
        var authServerUri = assertEnvVar("TEST_OIDC_AUTHSERVER");
        return Map.of(
                "mica.oidc.id", assertEnvVar("TEST_OIDC_CLIENTID"),
                "mica.oidc.clientSecret", assertEnvVar("TEST_OIDC_SECRET"),
                "mica.oidc.authorizationEndpoint", "%s/oauth2/v1/authorize".formatted(authServerUri),
                "mica.oidc.tokenEndpoint", "%s/oauth2/v1/token".formatted(authServerUri),
                "mica.oidc.userinfoEndpoint", "%s/oauth2/v1/userinfo".formatted(authServerUri),
                "mica.oidc.logoutEndpoint", "%s/login/signout".formatted(authServerUri));
    }
}
