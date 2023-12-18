package ca.ibodrov.mica.its;

import ca.ibodrov.mica.api.kinds.MicaKindV1;
import ca.ibodrov.mica.api.kinds.MicaViewV1;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.data.EntityStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.StartProcessResponse;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ca.ibodrov.mica.api.kinds.MicaViewV1.Data.jsonPath;
import static ca.ibodrov.mica.api.kinds.MicaViewV1.Selector.byEntityKind;
import static ca.ibodrov.mica.schema.ObjectSchemaNode.any;
import static ca.ibodrov.mica.schema.ObjectSchemaNode.object;
import static com.walmartlabs.concord.client2.ProcessEntry.StatusEnum.FINISHED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An integration test to simulate a real-world scenario: Mica as a
 * configuration management tool.
 */
public class ConfigManagementIT extends EndToEnd {

    private static EntityStore entityStore;
    private static ObjectMapper objectMapper;

    @BeforeAll
    public static void setUp() {
        var injector = micaServer.getServer().getInjector();
        entityStore = injector.getInstance(EntityStore.class);
        objectMapper = injector.getInstance(ObjectMapper.class);
    }

    @Test
    public void simulateGitflowPattern() throws Exception {
        // one-time setup

        // add an entity kind to represent a config layer
        entityStore.upsert(new MicaKindV1.Builder()
                .name("/acme/kinds/config-layer")
                .schema(object(Map.of("app", any()), Set.of("app")))
                .build()
                .toPartialEntity(objectMapper));

        // add a view to render the effective config
        entityStore.upsert(new MicaViewV1.Builder()
                .name("/acme/effective-configs/components/foobar/instance-config")
                .selector(byEntityKind("/acme/kinds/config-layer")
                        .withNamePatterns(List.of(
                                "/acme/configs/branches/main/instance-level-config.yaml",
                                "/acme/configs/pulls/${githubPr}/instance-level-config.yaml",
                                "/acme/configs/env/${env}/instance-level-config.yaml",
                                "/acme/configs/namespace/${namespace}/foobar/instance-config.yaml")))
                .data(jsonPath("$").withMerge())
                .build()
                .toPartialEntity(objectMapper));

        // add the base config and the env config layers
        entityStore.upsert(PartialEntity.create(
                "/acme/configs/branches/main/instance-level-config.yaml",
                "/acme/kinds/config-layer",
                Map.of("app", objectMapper.convertValue(Map.of("level", "base"), JsonNode.class))));
        entityStore.upsert(PartialEntity.create(
                "/acme/configs/env/ci/instance-level-config.yaml",
                "/acme/kinds/config-layer",
                Map.of("app", objectMapper.convertValue(Map.of("level", "ci"), JsonNode.class))));
        entityStore.upsert(PartialEntity.create(
                "/acme/configs/env/prod/instance-level-config.yaml",
                "/acme/kinds/config-layer",
                Map.of("app", objectMapper.convertValue(Map.of("level", "prod"), JsonNode.class))));

        // the "CI" side

        // post the updated config file to Mica
        // we expect prValue to be present in the PR configs only until the PR is
        // "merged"

        @Language("yaml")
        var configFile = """
                app:
                  level: pr
                  prValue: true
                  widgets: ["foo", "bar"]
                  gadgets:
                    x: 1
                    y: true
                """;
        var postThePrLayerConfig = startConcordProcess(Map.of(
                "arguments.githubPr", "123",
                "instance-level-config.yml", configFile.strip().getBytes(),
                "concord.yml", """
                        configuration:
                          runtime: "concord-v2"
                        flows:
                          default:
                            - task: mica
                              in:
                                action: upload
                                kind: /acme/kinds/config-layer
                                src: ${workDir}/instance-level-config.yml
                                name: /acme/configs/pulls/${githubPr}/instance-level-config.yaml
                        """.strip().getBytes()));
        assertFinished(postThePrLayerConfig);

        // CI "deployment"
        // render the effective config for CI

        var ciProcess = startConcordProcess(Map.of(
                "arguments.githubPr", "123",
                "concord.yml", """
                        configuration:
                          runtime: "concord-v2"
                        flows:
                          default:
                            - task: mica
                              in:
                                action: renderView
                                name: /acme/effective-configs/components/foobar/instance-config
                                parameters:
                                  githubPr: ${githubPr}
                                  env: ci
                              out: result
                            - log: name=${result.data[0].name}
                            - log: level=${result.data[0].app.level}
                            - log: prValue=${result.data[0].app.prValue}
                            - log: widgets=${result.data[0].app.widgets}
                        """.strip().getBytes()));
        assertFinished(ciProcess);
        var log = getProcessLog(ciProcess.getInstanceId());
        assertTrue(log.contains("name=/acme/configs/env/ci/instance-level-config.yaml"));
        assertTrue(log.contains("level=ci"));
        assertTrue(log.contains("prValue=true"));
        assertTrue(log.contains("widgets=[foo, bar]"));

        // merge into main
        // post the updated config as the new base config

        var postTheMainLayerConfig = startConcordProcess(Map.of(
                "arguments.githubPr", "123",
                "instance-level-config.yml", configFile.strip().getBytes(),
                "concord.yml", """
                        configuration:
                          runtime: "concord-v2"
                        flows:
                          default:
                            - task: mica
                              in:
                                action: upload
                                kind: /acme/kinds/config-layer
                                src: ${workDir}/instance-level-config.yml
                                name: /acme/configs/branches/main/instance-level-config.yaml
                        """.strip().getBytes()));
        assertFinished(postTheMainLayerConfig);

        // the actual deployment of the component
        // render the effective config for prod

        var prodProcess = startConcordProcess(Map.of(
                "arguments.githubPr", "123",
                "concord.yml", """
                        configuration:
                          runtime: "concord-v2"
                        flows:
                          default:
                            - task: mica
                              in:
                                action: renderView
                                name: /acme/effective-configs/components/foobar/instance-config
                                parameters:
                                  env: prod
                              out: result
                            - log: name=${result.data[0].name}
                            - log: level=${result.data[0].app.level}
                            - log: prValue=${result.data[0].app.prValue}
                            - log: widgets=${result.data[0].app.widgets}
                        """.strip().getBytes()));
        assertFinished(prodProcess);
        log = getProcessLog(prodProcess.getInstanceId());
        assertTrue(log.contains("name=/acme/configs/env/prod/instance-level-config.yaml"));
        assertTrue(log.contains("level=prod"));
        assertTrue(log.contains("prValue=true"));
    }

    private static StartProcessResponse startConcordProcess(Map<String, Object> request)
            throws ApiException {
        var processApi = new ProcessApi(concordClient);
        var taskUri = "mvn://ca.ibodrov.mica:mica-concord-task:%s".formatted(new Version().getMavenProjectVersion());
        var input = ImmutableMap.<String, Object>builder()
                .put("concord/_it.concord.yml", """
                        configuration:
                          runtime: "concord-v2"
                          dependencies:
                            - %s
                        """.formatted(taskUri).getBytes())
                .putAll(request)
                .build();
        return processApi.startProcess(input);
    }

    private static ProcessEntry assertFinished(StartProcessResponse startProcessResponse) throws ApiException {
        var process = new ProcessApi(concordClient).waitForCompletion(startProcessResponse.getInstanceId(),
                Duration.ofSeconds(60).toMillis());
        assertEquals(FINISHED, process.getStatus(), () -> getProcessLog(process.getInstanceId()));
        return process;
    }
}
