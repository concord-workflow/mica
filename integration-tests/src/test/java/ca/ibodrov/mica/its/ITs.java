package ca.ibodrov.mica.its;

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

import ca.ibodrov.mica.api.kinds.MicaKindV1;
import ca.ibodrov.mica.api.kinds.MicaViewV1;
import ca.ibodrov.mica.api.model.EntityId;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.api.model.RenderRequest;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.server.api.ViewResource;
import ca.ibodrov.mica.server.data.EntityStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Key;
import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.StartProcessResponse;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreDataManager;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreManager;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreRequest;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.org.project.ProjectManager;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import com.walmartlabs.concord.server.org.secret.SecretVisibility;
import com.walmartlabs.concord.server.security.UserSecurityContext;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import org.eclipse.jgit.api.Git;
import org.intellij.lang.annotations.Language;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.validation.ConstraintViolationException;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static ca.ibodrov.mica.api.kinds.MicaViewV1.Data.jsonPath;
import static ca.ibodrov.mica.api.kinds.MicaViewV1.Selector.byEntityKind;
import static ca.ibodrov.mica.server.data.BuiltinSchemas.INTERNAL_ENTITY_STORE_URI;
import static com.walmartlabs.concord.client2.ProcessEntry.StatusEnum.FINISHED;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

/**
 * Requires up-to-date version of mica-concord-task JAR in the local Maven
 * repository.
 * <p/>
 * Rebuild mica-concord-task with {@code mvn clean install} before running ITs.
 */
public class ITs extends TestResources {

    static OrganizationManager orgManager;
    static ProjectManager projectManager;
    static UserSecurityContext securityContext;
    static EntityStore entityStore;
    static ViewResource viewResource;
    static ObjectMapper objectMapper;
    static DSLContext dsl;
    static UUID adminId;
    static S3Client s3Client;

    static LocalStackContainer localStack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:s3-latest"))
            .withServices(S3);

    @BeforeAll
    public static void setUp() {
        var injector = micaServer.getServer().getInjector();

        orgManager = injector.getInstance(OrganizationManager.class);
        projectManager = injector.getInstance(ProjectManager.class);
        securityContext = injector.getInstance(UserSecurityContext.class);
        entityStore = injector.getInstance(EntityStore.class);
        viewResource = injector.getInstance(ViewResource.class);
        objectMapper = injector.getInstance(ObjectMapper.class);
        dsl = injector.getInstance(Key.get(DSLContext.class, MicaDB.class));

        adminId = injector.getInstance(UserManager.class)
                .getId("admin", null, UserType.LOCAL)
                .orElseThrow();

        localStack.start();

        s3Client = S3Client.builder()
                .endpointOverride(localStack.getEndpointOverride(S3))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                localStack.getAccessKey(),
                                localStack.getSecretKey())))
                .region(Region.of(localStack.getRegion()))
                .build();
    }

    @AfterAll
    public static void tearDown() {
        localStack.stop();
    }

    /**
     * An integration test to simulate a real-world scenario: Mica as a
     * configuration management tool.
     */
    @Test
    public void simulateGitflowPattern() throws Exception {
        // one-time setup

        // add an entity kind to represent a config layer
        upsert(new MicaKindV1.Builder()
                .name("/acme/kinds/config-layer")
                .schema(parseObject("""
                        properties:
                          app:
                            type: object
                        required: ["app"]
                        """))
                .build()
                .toPartialEntity(objectMapper));

        // add a view to render the effective config
        upsert(new MicaViewV1.Builder()
                .name("/acme/effective-configs/components/foobar/instance-config")
                .parameters(parseObject("""
                        properties:
                          githubPr:
                            type: string
                          env:
                            type: string
                          namespace:
                            type: string
                        """))
                .selector(byEntityKind("/acme/kinds/config-layer")
                        .withNamePatterns(List.of(
                                "/acme/configs/branches/main/instance-level-config.yaml",
                                "/acme/configs/pulls/${parameters.githubPr}/instance-level-config.yaml",
                                "/acme/configs/env/${parameters.env}/instance-level-config.yaml",
                                "/acme/configs/namespace/${parameters.namespace}/foobar/instance-config.yaml")))
                .data(jsonPath("$").withMerge())
                .build()
                .toPartialEntity(objectMapper));

        // add the base config and the env config layers
        upsert(PartialEntity.create(
                "/acme/configs/branches/main/instance-level-config.yaml",
                "/acme/kinds/config-layer",
                Map.of("app", objectMapper.convertValue(Map.of("level", "base"), JsonNode.class))));
        upsert(PartialEntity.create(
                "/acme/configs/env/ci/instance-level-config.yaml",
                "/acme/kinds/config-layer",
                Map.of("app", objectMapper.convertValue(Map.of("level", "ci"), JsonNode.class))));
        upsert(PartialEntity.create(
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

    @Test
    public void validateGitIncludesInViews() throws Exception {
        // add an entity kind to represent a config layer

        upsert(new MicaKindV1.Builder()
                .name("/acme/kinds/entity")
                .schema(parseObject("""
                        properties:
                          value:
                            type: string
                        required: ["value"]
                        """))
                .build()
                .toPartialEntity(objectMapper));

        // add some entities to the DB

        upsert(PartialEntity.create(
                "/test/foo",
                "/acme/kinds/entity",
                Map.of("value", TextNode.valueOf("foo!"))));

        upsert(PartialEntity.create(
                "/test/bar",
                "/acme/kinds/entity",
                Map.of("value", TextNode.valueOf("bar!"))));

        // prepare a bare Git repository with YAML files

        var repoBranch = "branch-" + UUID.randomUUID();
        var repoDir = Files.createTempDirectory("git");
        var pathInRepo = "nested-" + UUID.randomUUID();
        try (var git = Git.init()
                .setInitialBranch(repoBranch)
                .setDirectory(repoDir.toFile())
                .call()) {

            // create two entities in the initial branch

            var nestedDir = repoDir.resolve(pathInRepo);
            Files.createDirectory(nestedDir);

            Files.writeString(nestedDir.resolve("baz.yaml"), """
                    kind: /acme/kinds/entity
                    value: "baz!"
                    """);
            Files.writeString(nestedDir.resolve("qux.yaml"), """
                    kind: /acme/kinds/entity
                    value: "qux!"
                    """);
            git.add().addFilepattern(".").call();
            git.commit().setSign(false).setMessage("2 entities").call();

            // create one extra entity in a different branch

            git.checkout().setCreateBranch(true).setName("other").call();
            Files.writeString(nestedDir.resolve("fred.yaml"), """
                    kind: /acme/kinds/entity
                    value: "fred!"
                    """);
            git.add().addFilepattern(".").call();
            git.commit().setSign(false).setMessage("1 extra entity").call();
        }
        var repoUrl = "file://" + repoDir.toAbsolutePath();

        // create Concord org, project and repo

        var includeUri = securityContext.runAs(adminId, () -> {
            var orgName = "org-" + UUID.randomUUID();
            orgManager.createOrUpdate(new OrganizationEntry(orgName));

            var projectName = "project-" + UUID.randomUUID();
            var repoName = "repo-" + UUID.randomUUID();
            projectManager.createOrUpdate(orgName, new ProjectEntry(projectName,
                    Map.of(repoName,
                            new RepositoryEntry(new RepositoryEntry(repoName, repoUrl), repoBranch, null))));

            return URI.create("concord+git://%s/%s/%s?ref=%s&path=%s&useFileNames=true&namePrefix=%s"
                    .formatted(orgName, projectName, repoName, encode(repoBranch, UTF_8), encode(pathInRepo, UTF_8),
                            encode("/test/", UTF_8)));
        });

        // add a view to render both the entities from the DB and the ones from the Git
        // repo

        upsert(new MicaViewV1.Builder()
                .name("/acme/views/imports-demo")
                .parameters(parseObject("""
                        properties:
                          env:
                            type: string
                        """))
                .selector(byEntityKind("/acme/kinds/entity")
                        .withNamePatterns(List.of("/${parameters.env}/.*"))
                        .withIncludes(List.of(
                                INTERNAL_ENTITY_STORE_URI,
                                includeUri.toString())))
                .data(jsonPath("$.value"))
                .build()
                .toPartialEntity(objectMapper));

        // render the view

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
                                name: /acme/views/imports-demo
                                parameters:
                                  env: test
                              out: result
                            - log: ${result.data.stream().sorted().toList()}
                        """.strip().getBytes()));
        assertFinished(ciProcess);
        var log = getProcessLog(ciProcess.getInstanceId());
        assertTrue(log.contains("[bar!, baz!, foo!, qux!]"));
    }

    @Test
    public void batchDeleteUsingMicaTask() throws Exception {
        // add some entities to the DB

        var namePrefix = "/test" + System.currentTimeMillis();

        upsert(PartialEntity.create(
                namePrefix + "/aaa/foo",
                "/mica/record/v1",
                Map.of("value", TextNode.valueOf("foo!"))));

        upsert(PartialEntity.create(
                namePrefix + "/aaa/bar",
                "/mica/record/v1",
                Map.of("value", TextNode.valueOf("bar!"))));

        upsert(PartialEntity.create(
                namePrefix + "/bbb/baz",
                "/mica/record/v1",
                Map.of("value", TextNode.valueOf("baz!"))));

        // fetch entities using Mica task to verify they exist
        // delete entities in ${namePrefix}/aaa
        // verify that only ${namePrefix}/bbb/baz is left

        var ciProcess = startConcordProcess(Map.of(
                "arguments.namePrefix", namePrefix,
                "concord.yml", """
                        configuration:
                          runtime: "concord-v2"
                        flows:
                          default:
                            # grab all entities in ${namePrefix}
                            - task: mica
                              in:
                                action: listEntities
                                search: ${namePrefix}
                              out: result
                            - log: first=${result.data}

                            # delete entities in ${namePrefix}/aaa
                            - task: mica
                              in:
                                action: batch
                                operation: delete
                                namePatterns:
                                - ${namePrefix}/aaa/.*
                              out: result
                            - log: second=${result}

                            # grab all entities in ${namePrefix} again
                            - task: mica
                              in:
                                action: listEntities
                                search: ${namePrefix}
                              out: result
                            - log: third=${result.data}
                        """.strip().getBytes()));
        assertFinished(ciProcess);
        var logLines = getProcessLogLines(ciProcess.getInstanceId());
        var firstLine = logLines.stream().filter(s -> s.contains("first=")).findFirst().orElseThrow();
        assertTrue(firstLine.contains("name=" + namePrefix + "/aaa/foo"));
        assertTrue(firstLine.contains("name=" + namePrefix + "/aaa/bar"));
        assertTrue(firstLine.contains("name=" + namePrefix + "/bbb/baz"));

        var secondLine = logLines.stream().filter(s -> s.contains("second=")).findFirst().orElseThrow();
        assertTrue(secondLine.contains("name=" + namePrefix + "/aaa/foo"));
        assertTrue(secondLine.contains("name=" + namePrefix + "/aaa/bar"));
        assertFalse(secondLine.contains("name=" + namePrefix + "/bbb/baz"));

        var thirdLine = logLines.stream().filter(s -> s.contains("third=")).findFirst().orElseThrow();
        assertFalse(thirdLine.contains("name=" + namePrefix + "/aaa/foo"));
        assertFalse(thirdLine.contains("name=" + namePrefix + "/aaa/bar"));
        assertTrue(thirdLine.contains("name=" + namePrefix + "/bbb/baz"));
    }

    @Test
    public void upsertWithMergeUsingMicaTask() throws Exception {
        // add some entities to the DB

        var namePrefix = "/test" + System.currentTimeMillis();

        // insert the entity using Mica task, with {data={y=456}}
        var ciProcess = startConcordProcess(Map.of(
                "arguments.namePrefix", namePrefix,
                "concord.yml", """
                        configuration:
                          runtime: "concord-v2"
                        flows:
                          default:
                            - task: mica
                              in:
                                action: upsert
                                name: ${namePrefix}/aaa/foo
                                merge: false
                                entity:
                                  kind: /mica/record/v1
                                  data:
                                    y: 456
                              out: result
                            - log: initialVersion=${result.version.id}
                        """.strip().getBytes()));
        assertFinished(ciProcess);

        var logLines = getProcessLogLines(ciProcess.getInstanceId());
        var initialVersionLine = logLines.stream().filter(s -> s.contains("initialVersion=")).findFirst().orElseThrow();
        var initialVersionId = initialVersionLine.split("=")[1];

        // fetch the entity and verify that it's {data{y=456}}
        var entity = entityStore.getById(EntityId.fromString(initialVersionId)).orElseThrow();
        assertEquals(456, entity.data().get("data").get("y").asInt());

        // update the entity using Mica task, deep merge with {data={z=789}}
        ciProcess = startConcordProcess(Map.of(
                "arguments.namePrefix", namePrefix,
                "concord.yml", """
                        configuration:
                          runtime: "concord-v2"
                        flows:
                          default:
                            - task: mica
                              in:
                                action: upsert
                                name: ${namePrefix}/aaa/foo
                                merge: true
                                # we don't have to specify the kind here, it's already in the existing entity
                                entity:
                                  data:
                                    z: 789
                              out: result
                            - log: updatedVersion=${result.version.id}
                        """.strip().getBytes()));
        assertFinished(ciProcess);

        logLines = getProcessLogLines(ciProcess.getInstanceId());
        var updatedVersionLine = logLines.stream().filter(s -> s.contains("updatedVersion=")).findFirst().orElseThrow();
        var updatedVersionId = initialVersionLine.split("=")[1];
        assertEquals(initialVersionId, updatedVersionId);

        // fetch the entity and verify that it's {data{y=456, z=789}}
        entity = entityStore.getById(EntityId.fromString(updatedVersionId)).orElseThrow();
        assertEquals(456, entity.data().get("data").get("y").asInt());
        assertEquals(789, entity.data().get("data").get("z").asInt());
    }

    @Test
    public void requestsMustBeValidated() {
        assertThrows(ConstraintViolationException.class, () -> viewResource.render(RenderRequest.of("_invalid_")));
    }

    @Test
    public void nullParametersAreSkipped() throws Exception {
        // add a view an optional parameter "foo"
        upsert(new MicaViewV1.Builder()
                .name("/acme/views/optional-foo")
                .parameters(parseObject("""
                        properties:
                          foo:
                            type: string
                        """))
                .selector(byEntityKind("/mica/record/v1")
                        .withNamePatterns(List.of("${parameters.foo}")))
                .data(jsonPath("$"))
                .build()
                .toPartialEntity(objectMapper));

        // render the view setting "foo" to null
        // the task should skip the parameter and render the view without it

        var ciProcess = startConcordProcess(Map.of(
                "arguments.githubPr", "123",
                "concord.yml", """
                        configuration:
                          runtime: "concord-v2"
                        flows:
                          default:
                            - script: js
                              body: |
                                context.variables().set('foo', null);
                            - task: mica
                              in:
                                action: renderView
                                name: /acme/views/optional-foo
                                parameters:
                                  foo: ${foo}
                              out: result
                            - log: ${result}
                        """.strip().getBytes()));
        assertFinished(ciProcess);
        var log = getProcessLog(ciProcess.getInstanceId());
        assertTrue(log.contains("data=[]"));
    }

    @Test
    public void renderPropertiesWorksAsExpected() throws Exception {
        var entityName = "/test" + System.currentTimeMillis() + "/property-test";
        upsert(PartialEntity.create(
                entityName,
                "/mica/record/v1",
                Map.of("foo", objectMapper.readTree("""
                        {
                            "bar": "hello"
                        }
                        """),
                        "baz", TextNode.valueOf("world"))));

        // add a view an optional parameter "foo"
        upsert(new MicaViewV1.Builder()
                .name("/acme/views/properties")
                .selector(byEntityKind("/mica/record/v1")
                        .withNamePatterns(List.of(entityName)))
                .data(jsonPath("$"))
                .build()
                .toPartialEntity(objectMapper));

        // render the view setting "foo" to null
        // the task should skip the parameter and render the view without it

        var ciProcess = startConcordProcess(Map.of(
                "arguments.githubPr", "123",
                "concord.yml", """
                        configuration:
                          runtime: "concord-v2"
                        flows:
                          default:
                            - task: mica
                              in:
                                action: renderProperties
                                name: /acme/views/properties
                              out: result
                            - log: ${result}
                        """.strip().getBytes()));
        assertFinished(ciProcess);
        var log = getProcessLog(ciProcess.getInstanceId());
        assertTrue(log.contains("foo.bar=hello"));
        assertTrue(log.contains("baz=world"));
    }

    @Test
    public void viewsCanUseJsonStoreEntities() throws Exception {
        var orgName = "testOrg_" + System.currentTimeMillis();
        var storeName = "testStore_" + System.currentTimeMillis();
        var itemPath = "testItem_" + System.currentTimeMillis();

        securityContext.runAs(adminId, () -> {
            orgManager.createOrGet(orgName);

            var injector = micaServer.getServer().getInjector();
            var storeManager = injector.getInstance(JsonStoreManager.class);
            storeManager.createOrUpdate(orgName, JsonStoreRequest.builder()
                    .name(storeName)
                    .build());

            var dataManager = injector.getInstance(JsonStoreDataManager.class);
            dataManager.createOrUpdate(orgName, storeName, itemPath, parseMap("""
                    {
                        "items": ["foo", "bar", "baz"]
                    }
                    """));

            dataManager.createOrUpdate(orgName, storeName, itemPath + "_other", parseMap("""
                    {
                        "kind": "/something/else",
                        "items": ["qux"]
                    }
                    """));

            return null;
        });

        var entityKind = "/acme/kinds/json-store-entity";
        var includeUri = "concord+jsonstore://%s/%s?defaultKind=%s".formatted(orgName, storeName, entityKind);

        upsert(new MicaViewV1.Builder()
                .name("/acme/views/json-store-demo")
                .parameters(parseObject("""
                        properties:
                          env:
                            type: string
                        """))
                .selector(byEntityKind(entityKind)
                        .withIncludes(List.of(includeUri)))
                .data(jsonPath("$"))
                .build()
                .toPartialEntity(objectMapper));

        var result = securityContext.runAs(adminId,
                () -> viewResource.render(RenderRequest.of("/acme/views/json-store-demo")));
        var data = result.data().get("data");
        assertEquals(1, data.size());
        var item = data.get(0);
        assertEquals(itemPath, item.get("name").asText());
        assertEquals("foo", item.get("items").get(0).asText());
    }

    @Test
    public void viewsCanUseS3Entities() throws Exception {
        var bucketName = "testbucket" + System.currentTimeMillis();
        s3Client.createBucket(CreateBucketRequest.builder()
                .bucket(bucketName)
                .build());

        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucketName)
                .key("test.json")
                .build(), RequestBody.fromString("""
                        {
                            "foo": {
                                "bar": "baz"
                            }
                        }
                        """));

        var orgName = "testOrg_" + System.currentTimeMillis();
        var secretName = "testSecret_" + System.currentTimeMillis();

        securityContext.runAs(adminId, () -> {
            var org = orgManager.createOrGet(orgName);

            var injector = micaServer.getServer().getInjector();
            var secretManager = injector.getInstance(SecretManager.class);
            secretManager.createUsernamePassword(org.orgId(),
                    null,
                    secretName,
                    null,
                    localStack.getAccessKey(),
                    localStack.getSecretKey().toCharArray(),
                    SecretVisibility.PRIVATE,
                    "concord");
            return null;
        });

        var includeUri = "s3://%s/test.json?region=%s&secretRef=%s/%s&endpoint=%s"
                .formatted(bucketName, localStack.getRegion(), orgName, secretName,
                        localStack.getEndpointOverride(S3));

        upsert(new MicaViewV1.Builder()
                .name("/acme/views/s3-single-demo")
                .selector(byEntityKind(".*")
                        .withIncludes(List.of(includeUri)))
                .data(jsonPath("$"))
                .build()
                .toPartialEntity(objectMapper));

        var result = securityContext.runAs(adminId,
                () -> viewResource.render(RenderRequest.of("/acme/views/s3-single-demo")));
        var data = result.data().get("data");
        assertEquals(1, data.size());
        var item = data.get(0);
        assertEquals("baz", item.get("foo").get("bar").asText());
    }

    @Test
    public void sensitiveDataMustBeMasked() throws Exception {
        var orgName = "testOrg_" + System.currentTimeMillis();
        var secretName = "testSecret_" + System.currentTimeMillis();
        var secretString = "f00!";
        var otherSecretName = "otherTestSecret_" + System.currentTimeMillis();
        var otherSecretString = "b4r!";
        var secretKey = "k3y";
        var secretKeyName = "testSecretKey_" + System.currentTimeMillis();

        createBinarySecret(orgName, secretName, secretString.getBytes(UTF_8));
        createBinarySecret(orgName, otherSecretName, otherSecretString.getBytes(UTF_8));
        createBinarySecret(orgName, secretKeyName, secretKey.getBytes(UTF_8));

        var ciProcess = startConcordProcess(Map.of(
                "arguments.orgName", orgName,
                "arguments.secretName", secretName,
                "arguments.secretString", secretString,
                "arguments.otherSecretName", otherSecretName,
                "arguments.otherSecretString", otherSecretString,
                "arguments.secretKey", secretKey,
                "arguments.secretKeyName", secretKeyName,
                "concord.yml",
                """
                        configuration:
                          runtime: "concord-v2"
                        flows:
                          default:
                            # test "upsert"
                            - task: mica
                              in:
                                action: upsert
                                name: /masked-data
                                kind: /mica/record/v1
                                sensitiveDataExclusions:
                                  - ${otherSecretString}
                                entity:
                                  data:
                                    x: "${crypto.exportAsString(orgName, secretName, null)}_should_be_masked"
                                    y: "${crypto.exportAsString(orgName, otherSecretName, null)}_should_not_be_masked"
                                    k3y_should_be_masked_too: "${crypto.exportAsString(orgName, secretKeyName, null)}"
                            # test "upload"
                            - set:
                                testEntity:
                                  data:
                                    x: "${crypto.exportAsString(orgName, secretName, null)}_should_not_be_masked"
                                    y: "${crypto.exportAsString(orgName, otherSecretName, null)}_should_be_masked"
                                    k3y_should_be_masked_too: "${crypto.exportAsString(orgName, secretKeyName, null)}"
                                testFile: ${resource.writeAsYaml(testEntity)}
                            - task: mica
                              in:
                                action: upload
                                kind: /mica/record/v1
                                name: /masked-data-from-file
                                src: ${testFile}
                                sensitiveDataExclusions:
                                  - ${secretString}
                        """
                        .strip().getBytes()));
        assertFinished(ciProcess);

        var entity = entityStore.getByName("/masked-data").orElseThrow();
        assertEquals("_*****_should_be_masked", entity.data().get("data").get("x").asText());
        assertEquals(otherSecretString + "_should_not_be_masked",
                entity.data().get("data").get("y").asText());
        assertEquals("_*****", entity.data().get("data").get("_*****_should_be_masked_too").asText());

        var entityFromFile = entityStore.getByName("/masked-data-from-file").orElseThrow();
        assertEquals(secretString + "_should_not_be_masked",
                entityFromFile.data().get("data").get("x").asText());
        assertEquals("_*****_should_be_masked",
                entityFromFile.data().get("data").get("y").asText());
        assertEquals("_*****", entityFromFile.data().get("data").get("_*****_should_be_masked_too").asText());
    }

    @Test
    public void nameAndKindCanBeReplacedWhenUploading() throws Exception {
        @Language("yaml")
        var originalFile = """
                name: some_existing_name
                kind: some_kind_but_not_micas
                data: |
                  hi!
                """;
        var uploadFile = startConcordProcess(Map.of(
                "original-file.yml", originalFile.strip().getBytes(),
                "concord.yml", """
                        configuration:
                          runtime: "concord-v2"
                        flows:
                          default:
                            - task: mica
                              in:
                                action: upload
                                kind: /mica/record/v1
                                src: ${workDir}/original-file.yml
                                name: /result-file.yaml
                        """.strip().getBytes()));
        assertFinished(uploadFile);

        var entity = entityStore.getByName("/result-file.yaml").orElseThrow();
        assertEquals("/mica/record/v1", entity.kind());
        var doc = entityStore.getEntityDoc(entity.version()).orElseThrow();
        assertTrue(doc.contains("name: \"/result-file.yaml\""));
        assertTrue(doc.contains("kind: \"/mica/record/v1\""));
    }

    @Test
    public void uploadWithReplace() throws Exception {
        // upload the initial version

        @Language("yaml")
        var initialDoc = """
                name: /test/upload-replace
                kind: /mica/record/v1
                data: |
                  initial doc
                """;
        var uploadInitial = startConcordProcess(Map.of(
                "initial-file.yml", initialDoc.strip().getBytes(),
                "concord.yml", """
                        configuration:
                          runtime: "concord-v2"
                        flows:
                          default:
                            - task: mica
                              in:
                                action: upload
                                src: ${workDir}/initial-file.yml
                        """.strip().getBytes()));
        assertFinished(uploadInitial);

        // grab the uploaded entity and the doc

        var uploadedEntity = entityStore.getByName("/test/upload-replace").orElseThrow();
        var uploadedInitialDoc = entityStore.getEntityDoc(uploadedEntity.version()).orElseThrow();

        // upload the updated doc with replace

        var updatedDoc = uploadedInitialDoc.replace("initial", "updated");
        var uploadUpdated = startConcordProcess(Map.of(
                "updated-file.yml", updatedDoc.strip().getBytes(),
                "concord.yml", """
                        configuration:
                          runtime: "concord-v2"
                        flows:
                          default:
                            - task: mica
                              in:
                                action: upload
                                replace: true
                                src: ${workDir}/updated-file.yml
                        """.strip().getBytes()));
        assertFinished(uploadUpdated);

        // grab the updated doc

        var uploadedUpdatedDoc = entityStore.getLatestEntityDoc(uploadedEntity.id()).orElseThrow();
        assertTrue(uploadedUpdatedDoc.contains("updated doc"));

        // check the entity

        var updatedEntity = entityStore.getById(uploadedEntity.id()).orElseThrow();
        assertTrue(updatedEntity.deletedAt().isEmpty());
    }

    @Test
    public void nameAndKindCanBeReplacedWhenUploadingAndItWorksWithMasking() throws Exception {
        upsert(new MicaKindV1.Builder()
                .name("/acme/config")
                .schema(parseObject("""
                        properties:
                          acme:
                            properties:
                              fooSecret:
                                type: string
                              barSecret:
                                type: string
                            required: ["fooSecret", "barSecret"]
                        required: ["acme"]
                        """))
                .build()
                .toPartialEntity(objectMapper));

        var orgName = "testOrg_" + System.currentTimeMillis();
        var fooSecretName = "fooSecret_" + System.currentTimeMillis();
        var fooSecret = "f00!";
        var barSecretName = "barSecret_" + System.currentTimeMillis();
        var barSecret = "b4r!";

        createBinarySecret(orgName, fooSecretName, fooSecret.getBytes(UTF_8));
        createBinarySecret(orgName, barSecretName, barSecret.getBytes(UTF_8));

        var ciProcess = startConcordProcess(Map.of(
                "arguments.orgName", orgName,
                "arguments.fooSecretName", fooSecretName,
                "arguments.fooSecret", fooSecret,
                "arguments.barSecretName", barSecretName,
                "arguments.barSecret", barSecret,
                "concord.yml",
                """
                        configuration:
                          runtime: "concord-v2"
                        flows:
                          default:
                            - set:
                                acmeId: "boom"
                                entityName: "/acme/${acmeId}.yaml"
                                entity:
                                  apiVersion: "config.acme.com/v1"
                                  kind: "AcmeConfig"
                                  acme:
                                    fooSecret: "${crypto.exportAsString(orgName, fooSecretName, null)}"
                                    barSecret: "${crypto.exportAsString(orgName, barSecretName, null)}"
                                testFile: ${resource.writeAsYaml(entity)}
                            - task: mica
                              in:
                                action: upload
                                kind: /acme/config
                                name: ${entityName}
                                src: ${testFile}
                                sensitiveDataExclusions:
                                  - ${barSecret}
                        """
                        .strip().getBytes()));
        assertFinished(ciProcess);

        var entity = entityStore.getByName("/acme/boom.yaml").orElseThrow();
        assertEquals("/acme/config", entity.kind());

        var doc = entityStore.getEntityDoc(entity.version()).orElseThrow();
        assertTrue(doc.contains("name: \"/acme/boom.yaml\""));
        assertTrue(doc.contains("kind: \"/acme/config\""));
        assertTrue(doc.contains("fooSecret: \"_*****\""));
        assertTrue(doc.contains("barSecret: \"b4r!\""));
    }

    @Test
    public void taskMustCorrectlySerializeTimestamps() throws Exception {
        @Language("yaml")
        var originalFile = """
                data: hi!
                """;

        var proc = startConcordProcess(Map.of(
                "original-file.yml", originalFile.strip().getBytes(),
                "concord.yml", """
                        configuration:
                          runtime: "concord-v2"
                        flows:
                          default:
                            - task: mica
                              in:
                                action: upload
                                kind: /mica/record/v1
                                src: ${workDir}/original-file.yml
                                name: /taskMustCorrectlySerializeTimestamps/result-file.yaml
                              out: result
                            - log: "Result: ${result}"
                        """.strip().getBytes()));
        assertFinished(proc);

        var log = getProcessLog(proc.getInstanceId());
        var currentYear = String.valueOf(Year.now().getValue());
        assertTrue(log.matches("(?s).*Result:.*updatedAt=" + currentYear + "-.*"));
    }

    private static void createBinarySecret(String orgName, String secretName, byte[] secret) throws Exception {
        securityContext.runAs(adminId, () -> {
            var orgId = orgManager.createOrGet(orgName).orgId();
            var injector = micaServer.getServer().getInjector();
            var secretManager = injector.getInstance(SecretManager.class);
            secretManager.createBinaryData(orgId, Set.of(), secretName, null, new ByteArrayInputStream(secret),
                    SecretVisibility.PUBLIC, "concord");
            return null;
        });
    }

    private static void upsert(PartialEntity entity) {
        dsl.transaction(tx -> entityStore.upsert(tx.dsl(), entity, null).orElseThrow());
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

    private static ObjectNode parseObject(@Language("yaml") String s) {
        try {
            return objectMapper.copyWith(new YAMLFactory()).readValue(s, ObjectNode.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Object> parseMap(@Language("yaml") String s) {
        try {
            var jsonLikeMap = objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
            return objectMapper.readValue(s, jsonLikeMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
