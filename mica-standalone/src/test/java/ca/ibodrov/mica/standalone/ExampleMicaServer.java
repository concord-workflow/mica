package ca.ibodrov.mica.standalone;

import com.google.common.collect.ImmutableMap;
import org.testcontainers.containers.PostgreSQLContainer;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

public class ExampleMicaServer {

    private static final String ADMIN_API_KEY = "mica";

    public static void main(String[] args) throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:15-alpine")) {
            db.start();
            try (var server = new MicaServer(prepareConfig(db))) {
                server.start();
                System.out.printf("""
                        ==============================================================

                          UI: http://localhost:8080/mica/
                          DB:
                            JDBC URL: %s
                            username: %s
                            password: %s
                        %n""", db.getJdbcUrl(),
                        db.getUsername(),
                        db.getPassword());

                Thread.currentThread().join();
            }
        }
    }

    private static Map<String, String> prepareConfig(PostgreSQLContainer<?> db) {
        var authServerUri = assertEnv("MICA_OIDC_AUTHSERVER");
        var oidcClientId = assertEnv("MICA_OIDC_CLIENTID");
        var oidcSecret = assertEnv("MICA_OIDC_SECRET");
        return ImmutableMap.<String, String>builder()
                .put("server.port", String.valueOf(8080))
                .put("db.url", db.getJdbcUrl())
                .put("db.appUsername", db.getUsername())
                .put("db.appPassword", db.getPassword())
                .put("db.inventoryUsername", db.getUsername())
                .put("db.inventoryPassword", db.getPassword())
                .put("db.changeLogParameters.defaultAdminToken", ADMIN_API_KEY)
                .put("secretStore.serverPassword", randomString(64))
                .put("secretStore.secretStoreSalt", randomString(64))
                .put("secretStore.projectSecretSalt", randomString(64))
                .put("oidc.enabled", "true")
                .put("oidc.clientId", oidcClientId)
                .put("oidc.secret", oidcSecret)
                .put("oidc.discoveryUri", authServerUri + "/.well-known/openid-configuration")
                .put("oidc.afterLogoutUrl", "http://localhost:8080/mica/")
                .put("oidc.urlBase", "http://localhost:8080")
                .build();
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
