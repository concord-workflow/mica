package ca.ibodrov.mica.standalone;

import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.OrganizationVisibility;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreDataManager;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreManager;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreRequest;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreVisibility;
import com.walmartlabs.concord.server.security.UserSecurityContext;
import com.walmartlabs.concord.server.user.UserDao;
import com.walmartlabs.concord.server.user.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

public class ExampleMicaServer {

    private static final Logger log = LoggerFactory.getLogger(ExampleMicaServer.class);

    private static final String ADMIN_API_TOKEN = "mica";

    public static void main(String[] args) throws Exception {
        var authServerUri = assertEnv("MICA_AUTH_SERVER_URI");
        var oidcClientId = assertEnv("MICA_OIDC_CLIENT_ID");
        var oidcSecret = assertEnv("MICA_OIDC_SECRET");

        var generateRandomData = Optional.ofNullable(System.getenv("GENERATE_RANDOM_DATA"))
                .map(Boolean::parseBoolean)
                .orElse(false);

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

                createExampleResources(server);
                if (generateRandomData) {
                    server.getInjector().getInstance(RandomDataGenerator.class).generate(100000);
                }

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

    private static void createExampleResources(MicaServer server) throws Exception {
        var injector = server.getInjector();

        var userDao = injector.getInstance(UserDao.class);
        var adminId = userDao.getId("admin", null, UserType.LOCAL);

        var securityContext = injector.getInstance(UserSecurityContext.class);
        securityContext.runAs(adminId, () -> {
            var orgName = "example-org";
            var jsonStoreName = "example-store";

            var orgManager = injector.getInstance(OrganizationManager.class);
            var orgEntry = new OrganizationEntry(null, orgName, null, OrganizationVisibility.PUBLIC,
                    Map.of("foo", "bar"), Map.of("baz", "qux"));
            orgManager.createOrUpdate(orgEntry);

            var jsonStoreManager = injector.getInstance(JsonStoreManager.class);
            jsonStoreManager.createOrUpdate(orgName, JsonStoreRequest.builder()
                    .name(jsonStoreName)
                    .visibility(JsonStoreVisibility.PUBLIC)
                    .build());

            var jsonStoreDataManager = injector.getInstance(JsonStoreDataManager.class);
            jsonStoreDataManager.createOrUpdate(orgName, jsonStoreName, "/test.yaml", Map.of(
                    "name", "/acme/records/foo",
                    "kind", "/mica/record/v1",
                    "data", Map.of(
                            "value", "foo",
                            "xyz", 123)));

            return null;
        });
    }
}
