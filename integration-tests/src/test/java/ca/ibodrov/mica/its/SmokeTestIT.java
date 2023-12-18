package ca.ibodrov.mica.its;

import ca.ibodrov.mica.db.MicaDB;
import com.google.inject.Key;
import com.walmartlabs.concord.client2.ProcessApi;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.client2.ProcessEntry.StatusEnum.FINISHED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A simple integration test to validate the following:
 * mica-concord-server-plugin is loaded and available - concord-server can still
 * run processes - mica can be accessed by concord-agents
 */
public class SmokeTestIT extends EndToEnd {

    /**
     * Grab the mica-server version from /api/mica/v1/system and compare it to the
     * version in integration-tests pom.xml. A simple sanity check that we're
     * actually testing the right version.
     */
    @Test
    public void testMicaAvailable() {
        var testVersion = new Version().getGitCommitId();
        var systemVersion = micaClient.getSystemInfo().commitId();
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
        var client1Name = "smokeClient1";
        var client2Id = UUID.randomUUID();
        var client2Name = "smokeClient2";

        var dsl = micaServer.getServer().getInjector().getInstance(Key.get(DSLContext.class, MicaDB.class));
        dsl.transaction(tx -> {
            TestData.insertEntity(tx, client1Id, client1Name, "client", "{\"foo\": \"bar\"}");
            TestData.insertEntity(tx, client2Id, client2Name, "client", "{\"baz\": \"qux\"}");
        });

        // start the process

        var processApi = new ProcessApi(concordClient);
        var taskUri = "mvn://ca.ibodrov.mica:mica-concord-task:%s".formatted(new Version().getMavenProjectVersion());

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
                        search: "smoke"
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
}
