package ca.ibodrov.mica.its;

import ca.ibodrov.mica.api.client.MicaApiClient;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.testing.TestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.inject.Key;
import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ApiClientConfiguration;
import com.walmartlabs.concord.client2.DefaultApiClientFactory;
import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.it.testingserver.TestingConcordAgent;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.client2.ProcessEntry.StatusEnum.FINISHED;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A simple integration test to validate the following:
 * mica-concord-server-plugin is loaded and available - concord-server can still
 * run processes - mica can be accessed by concord-agents
 */
public class SmokeTestIT {

    private static PostgreSQLContainer<?> db;
    private static TestingMicaServer micaServer;
    private static TestingConcordAgent concordAgent;
    private static MicaApiClient micaClient;
    private static ApiClient concordClient;

    @BeforeAll
    public static void setUp() throws Exception {
        db = new PostgreSQLContainer<>("postgres:15-alpine");
        db.start();

        micaServer = TestingMicaServer.withFakeOidc(db, getFreePort());
        micaServer.start();

        concordAgent = new TestingConcordAgent(micaServer,
                Map.of("runnerV2.path", findRunnerV2Jar()),
                List.of());
        concordAgent.start();

        var objectMapper = new ObjectMapper().registerModule(new Jdk8Module());
        micaClient = new MicaApiClient(objectMapper, micaServer.getApiBaseUrl(), micaServer.getAdminApiKey());
        concordClient = new DefaultApiClientFactory(micaServer.getApiBaseUrl())
                .create(ApiClientConfiguration.builder()
                        .apiKey(micaServer.getAdminApiKey())
                        .build());
    }

    @AfterAll
    public static void tearDown() throws Exception {
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

    /**
     * Grab the mica-server version from /api/mica/v1/system and compare it to the
     * version in integration-tests pom.xml. A simple sanity check that we're
     * actually testing the right version.
     */
    @Test
    public void testMicaAvailable() {
        // TODO compare commitIds instead
        var testVersion = new Version().getMicaITsVersion();
        var systemVersion = micaClient.getSystemInfo().version();
        assertEquals(testVersion, systemVersion);
    }

    /**
     * Test that adding Mica didn't break concord-server entirely. It only tests a
     * very basic process, but it's better than nothing.
     */
    @Test
    public void testConcordCanRunProcesses() throws Exception {
        var processApi = new ProcessApi(concordClient);
        var response = processApi.startProcess(Map.of("concord.yml", """
                configuration:
                  runtime: "concord-v2"
                flows:
                  default:
                    - log: "Hello!"
                """.getBytes()));
        assertNotNull(response.getInstanceId());

        var process = processApi.waitForCompletion(response.getInstanceId(), Duration.ofSeconds(60).toMillis());
        assertEquals(FINISHED, process.getStatus(), () -> getProcessLog(process.getInstanceId()));
    }

    /**
     * Test that "mica" task can reach Mica API. It checks the version of "mica"
     * task if it matches the version in integration-tests pom.xml Note: the test
     * requires mica-concord-task artifact to be installed in local Maven repository
     * prior to execution.
     */
    @Test
    public void testConcordAgentCanReachMica() throws Exception {
        // prepare some data

        var client1Id = UUID.randomUUID();
        var client1Name = "client1";
        var client2Id = UUID.randomUUID();
        var client2Name = "client2";

        var dsl = micaServer.getServer().getInjector().getInstance(Key.get(DSLContext.class, MicaDB.class));
        dsl.transaction(tx -> {
            TestData.insertEntity(tx, client1Id, client1Name, "client", "{\"foo\": \"bar\"}");
            TestData.insertEntity(tx, client2Id, client2Name, "client", "{\"baz\": \"qux\"}");
        });

        // start the process

        var processApi = new ProcessApi(concordClient);
        var taskUri = "mvn://ca.ibodrov.mica:mica-concord-task:%s".formatted(new Version().getMicaITsVersion());

        var response = processApi.startProcess(Map.of("concord.yml", """
                configuration:
                  runtime: "concord-v2"
                  dependencies:
                    - %s
                flows:
                  default:
                    - task: mica
                      in:
                        action: listEntities
                      out: result

                    - script: js
                      in:
                        # TODO figure out why passing the result by value doesn't work
                        # (I'm using resource.printJson(result.data) as a workaround)
                        json: ${resource.printJson(result.data)}
                      body: |
                        if (!json) {
                          throw new Error('Expected json');
                        }

                        var data = JSON.parse(json);

                        var ok = true;
                        ok = ok && data.length === 2;
                        ok = ok && data[0]?.id === '%s';
                        ok = ok && data[0]?.name === '%s';
                        ok = ok && data[1]?.id === '%s';
                        ok = ok && data[1]?.name === '%s';

                        if (!ok) {
                          throw new Error('Unexpected result: ' + json);
                        }
                """.formatted(taskUri, client1Id, client1Name, client2Id, client2Name)
                .getBytes()));
        assertNotNull(response.getInstanceId());

        var process = processApi.waitForCompletion(response.getInstanceId(), Duration.ofSeconds(60).toMillis());
        assertEquals(FINISHED, process.getStatus(), () -> getProcessLog(process.getInstanceId()));
    }

    private String getProcessLog(UUID instanceId) {
        try {
            var log = new ProcessApi(concordClient)
                    .getProcessLog(instanceId, null);
            return new String(log.readAllBytes());
        } catch (Exception e) {
            return "Can't show the details, got an error while retrieving the process log: " + e.getMessage();
        }
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
