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

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public final class Configuration {

    private final Map<String, String> config = new HashMap<>();

    private boolean databaseConfigured = false;
    private boolean oidcConfigured = false;
    private boolean secretsConfigured = false;
    private boolean serverPortConfigured = false;
    private boolean dataDirConfigured = false;

    public Map<String, String> toMap() {
        if (!databaseConfigured) {
            throw new IllegalStateException("Database is not configured");
        }

        if (!oidcConfigured) {
            throw new IllegalStateException("OIDC is not configured");
        }

        if (!secretsConfigured) {
            throw new IllegalStateException("Secrets are not configured");
        }

        if (!serverPortConfigured) {
            throw new IllegalStateException("Server port is not configured");
        }

        if (!dataDirConfigured) {
            throw new IllegalStateException("Data directory is not configured");
        }

        return config;
    }

    public Configuration configureDatabaseUsingEnv() {
        var dbUrl = getEnv("MICA_DB_URL", "jdbc:postgresql://localhost:5432/postgres");
        var dbUsername = getEnv("MICA_DB_USERNAME", "postgres");
        var dbPassword = assertEnv("MICA_DB_PASSWORD");
        var defaultAdminToken = getEnv("MICA_DEFAULT_ADMIN_TOKEN", null);
        var createExtensionAvailable = getEnv("MICA_DB_CREATE_EXTENSIONS", "true");
        return this.configureDatabase(dbUrl, dbUsername, dbPassword, defaultAdminToken,
                Boolean.parseBoolean(createExtensionAvailable));
    }

    public Configuration configureDatabase(String dbUrl,
                                           String dbUsername,
                                           String dbPassword,
                                           @Nullable String defaultAdminToken,
                                           boolean createExtensionAvailable) {
        config.put("db.url", dbUrl);
        config.put("db.appUsername", dbUsername);
        config.put("db.appPassword", dbPassword);
        config.put("db.inventoryUsername", dbPassword);
        config.put("db.inventoryPassword", dbPassword);
        config.put("db.changeLogParameters.superuserAvailable", "false");
        if (defaultAdminToken != null && !defaultAdminToken.isBlank()) {
            config.put("db.changeLogParameters.defaultAdminToken", defaultAdminToken);
        }
        config.put("db.changeLogParameters.createExtensionAvailable", Boolean.toString(createExtensionAvailable));
        this.databaseConfigured = true;
        return this;
    }

    public Configuration configureOidcUsingEnv() {
        var baseUrl = assertEnv("MICA_BASE_URL");
        var authServerUri = assertEnv("MICA_AUTH_SERVER_URI");
        var oidcClientId = assertEnv("MICA_OIDC_CLIENT_ID");
        var oidcSecret = assertEnv("MICA_OIDC_SECRET");
        return this.configureOidc(baseUrl, authServerUri, oidcClientId, oidcSecret);
    }

    public Configuration configureOidc(String baseUrl, String authServerUri, String oidcClientId, String oidcSecret) {
        config.put("oidc.enabled", "true");
        config.put("oidc.clientId", oidcClientId);
        config.put("oidc.secret", oidcSecret);
        config.put("oidc.discoveryUri", authServerUri + "/.well-known/openid-configuration");
        config.put("oidc.urlBase", baseUrl);
        config.put("oidc.afterLoginUrl", baseUrl + "/mica/");
        config.put("oidc.afterLogoutUrl", baseUrl + "/mica/");
        config.put("oidc.onErrorUrl", baseUrl + "/#/unauthorized");
        this.oidcConfigured = true;
        return this;
    }

    public Configuration configureSecretsUsingEnv() {
        var sharedSecret = assertEnv("MICA_SHARED_SECRET");
        return this.configureSecrets(sharedSecret);
    }

    public Configuration configureSecrets(String sharedSecret) {
        config.put("secretStore.serverPassword", sharedSecret);
        config.put("secretStore.secretStoreSalt", sharedSecret);
        config.put("secretStore.projectSecretSalt", sharedSecret);
        this.secretsConfigured = true;
        return this;
    }

    public Configuration configureServerPortUsingEnv() {
        var serverPort = getEnv("MICA_SERVER_PORT", "8080");
        config.put("server.port", serverPort);
        this.serverPortConfigured = true;
        return this;
    }

    public Configuration configureDataDirUsingEnv() {
        var dataDir = getEnv("MICA_DATA_DIR", "/tmp/mica");
        config.put("repositoryCache.cacheDir", dataDir + "/repositoryCache");
        config.put("repositoryCache.cacheInfoDir", dataDir + "/repositoryCacheInfo");
        this.dataDirConfigured = true;
        return this;
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
}
