package ca.ibodrov.mica.its;

import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ApiClientConfiguration;
import com.walmartlabs.concord.client2.DefaultApiClientFactory;
import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.it.testingserver.TestingConcordAgent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

abstract class TestResources {

    protected static PostgreSQLContainer<?> db;
    protected static TestingMicaServer micaServer;
    protected static TestingConcordAgent concordAgent;
    protected static ApiClient concordClient;

    @BeforeAll
    public static void setUpServers() throws Exception {
        db = new PostgreSQLContainer<>("postgres:15-alpine");
        db.start();

        micaServer = TestingMicaServer.withFakeOidc(db, getFreePort());
        micaServer.start();

        var tempDir = Files.createTempDirectory("concord-agent");
        concordAgent = new TestingConcordAgent(micaServer, Map.of(
                "workDirBase", tempDir.toAbsolutePath().toString(),
                "runnerV2.path", findRunnerV2Jar()), List.of());
        concordAgent.start();

        concordClient = new DefaultApiClientFactory(micaServer.getApiBaseUrl())
                .create(ApiClientConfiguration.builder()
                        .apiKey(micaServer.getAdminApiKey())
                        .build());
    }

    @AfterAll
    public static void tearDownServers() throws Exception {
        if (concordAgent != null) {
            concordAgent.stop();
            concordAgent = null;
        }

        if (micaServer != null) {
            micaServer.stop();
            micaServer = null;
        }

        if (db != null) {
            db.stop();
            db = null;
        }
    }

    protected static String getProcessLog(UUID instanceId) {
        try {
            var log = new ProcessApi(concordClient).getProcessLog(instanceId, null);
            return new String(log.readAllBytes());
        } catch (Exception e) {
            return "Can't show the details, got an error while retrieving the process log: " + e.getMessage();
        }
    }

    protected static List<String> getProcessLogLines(UUID instanceId) {
        var log = getProcessLog(instanceId);
        return List.of(log.split("\\n"));
    }

    private static int getFreePort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String findRunnerV2Jar() {
        var pwd = requireNonNull(System.getProperty("user.dir"), "Can't determine user.dir");
        var path = "%s/target/deps/runner-v2.jar".formatted(pwd);
        if (!Files.exists(Paths.get(path))) {
            throw new RuntimeException("Can't find the runner v2 JAR in " + path);
        }
        return path;
    }
}
