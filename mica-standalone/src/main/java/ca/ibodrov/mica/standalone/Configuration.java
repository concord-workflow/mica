package ca.ibodrov.mica.standalone;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public final class Configuration {

    public static Map<String, String> fromEnv() {
        var serverPort = getEnv("MICA_SERVER_PORT", "8080");
        var baseUrl = assertEnv("MICA_BASE_URL");
        var dbUrl = getEnv("MICA_DB_URL", "jdbc:postgresql://localhost:5432/postgres");
        var dbUsername = getEnv("MICA_DB_USERNAME", "postgres");
        var dbPassword = assertEnv("MICA_DB_PASSWORD");
        var sharedSecret = assertEnv("MICA_SHARED_SECRET");
        var authServerUri = assertEnv("MICA_AUTH_SERVER_URI");
        var oidcClientId = assertEnv("MICA_OIDC_CLIENT_ID");
        var oidcSecret = assertEnv("MICA_OIDC_SECRET");
        return ImmutableMap.<String, String>builder()
                .put("server.port", serverPort)
                .put("db.url", dbUrl)
                .put("db.appUsername", dbUsername)
                .put("db.appPassword", dbPassword)
                .put("db.inventoryUsername", dbPassword)
                .put("db.inventoryPassword", dbPassword)
                .put("secretStore.serverPassword", sharedSecret)
                .put("secretStore.secretStoreSalt", sharedSecret)
                .put("secretStore.projectSecretSalt", sharedSecret)
                .put("oidc.enabled", "true")
                .put("oidc.clientId", oidcClientId)
                .put("oidc.secret", oidcSecret)
                .put("oidc.discoveryUri", authServerUri + "/.well-known/openid-configuration")
                .put("oidc.afterLogoutUrl", baseUrl + "/mica/")
                .put("oidc.urlBase", baseUrl)
                .build();
    }

    private static String getEnv(String key, String defaultValue) {
        var v = System.getenv(key);
        if (v == null) {
            return defaultValue;
        }
        return v;
    }

    private static String assertEnv(String key) {
        var v = System.getenv(key);
        if (v == null) {
            throw new RuntimeException("Missing %s environment variable".formatted(key));
        }
        return v;
    }

    private Configuration() {
    }
}
