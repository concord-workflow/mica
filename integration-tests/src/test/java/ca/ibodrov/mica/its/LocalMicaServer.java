package ca.ibodrov.mica.its;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

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
        var oidcClientId = assertEnvVar("TEST_OIDC_CLIENTID");
        var oidcSecret = assertEnvVar("TEST_OIDC_SECRET");
        return ImmutableMap.<String, String>builder()
                .put("oidc.enabled", "true")
                .put("oidc.clientId", oidcClientId)
                .put("oidc.secret", oidcSecret)
                .put("oidc.discoveryUri", authServerUri + "/.well-known/openid-configuration")
                .put("oidc.urlBase", "http://localhost:8001")
                .put("db.changeLogParameters.defaultAdminToken", "mica")
                .put("mica.oidc.clientId", oidcClientId)
                .put("mica.oidc.clientSecret", oidcSecret)
                .put("mica.oidc.authorizationEndpoint", authServerUri + "/oauth2/v1/authorize")
                .put("mica.oidc.tokenEndpoint", authServerUri + "/oauth2/v1/token")
                .put("mica.oidc.userinfoEndpoint", authServerUri + "/oauth2/v1/userinfo")
                .put("mica.oidc.logoutEndpoint", authServerUri + "/login/signout")
                .build();
    }
}
