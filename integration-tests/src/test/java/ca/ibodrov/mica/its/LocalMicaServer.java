package ca.ibodrov.mica.its;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

/**
 * Basic concord-server plus Mica. Run it in your IDE.
 */
public class LocalMicaServer {

    private static final String TEST_ADMIN_TOKEN = "mica";

    public static void main(String[] args) throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:15-alpine");
                var server = new TestingMicaServer(db, 8080, createConfig())) {
            db.start();
            server.start();
            System.out.printf("""
                    ==============================================================

                      UI (hosted): http://localhost:8080/mica/
                      UI (dev): http://localhost:5173/mica/ (launched separately)
                      DB:
                        JDBC URL: %s
                        username: %s
                        password: %s
                      API:
                        admin key: %s

                      curl -i -H 'Authorization: %s' http://localhost:8080/api/mica/v1/system

                    ==============================================================
                    %n""", db.getJdbcUrl(), db.getUsername(), db.getPassword(), TEST_ADMIN_TOKEN, TEST_ADMIN_TOKEN);
            Thread.currentThread().join();
        }
    }

    private static String assertEnvVar(String key) {
        return Optional.ofNullable(System.getenv(key))
                .orElseThrow(() -> new RuntimeException("Missing %s env var".formatted(key)));
    }

    private static Map<String, String> createConfig() {
        var authServerUri = assertEnvVar("TEST_OIDC_AUTHSERVER");
        var oidcClientId = assertEnvVar("TEST_OIDC_CLIENTID");
        var oidcSecret = assertEnvVar("TEST_OIDC_SECRET");
        return ImmutableMap.<String, String>builder()
                .put("oidc.enabled", "true")
                .put("oidc.clientId", oidcClientId)
                .put("oidc.secret", oidcSecret)
                .put("oidc.discoveryUri", authServerUri + "/.well-known/openid-configuration")
                .put("oidc.afterLogoutUrl", "http://localhost:8080/mica/")
                .put("oidc.urlBase", "http://localhost:8080")
                .put("db.changeLogParameters.defaultAdminToken", TEST_ADMIN_TOKEN)
                .build();
    }
}
