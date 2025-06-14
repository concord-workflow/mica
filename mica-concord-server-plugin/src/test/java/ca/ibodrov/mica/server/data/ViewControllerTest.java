package ca.ibodrov.mica.server.data;

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
import ca.ibodrov.mica.api.model.ApiError;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.api.model.RenderViewRequest;
import ca.ibodrov.mica.server.AbstractDatabaseTest;
import ca.ibodrov.mica.server.data.js.NoopJsEvaluator;
import ca.ibodrov.mica.server.data.viewRenderHistory.ViewRenderHistoryController;
import ca.ibodrov.mica.server.exceptions.ApiException;
import ca.ibodrov.mica.server.reports.ValidateAllReport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Streams;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static ca.ibodrov.mica.api.kinds.MicaViewV1.Data.jsonPath;
import static ca.ibodrov.mica.api.kinds.MicaViewV1.Selector.byEntityKind;
import static ca.ibodrov.mica.api.kinds.MicaViewV1.Validation.asEntityKind;
import static javax.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static org.junit.jupiter.api.Assertions.*;

public class ViewControllerTest extends AbstractDatabaseTest {

    private static ViewController viewController;

    @BeforeAll
    public static void setUp() {
        var entityKindStore = new EntityKindStore(entityStore);
        var internalEntityFetcher = new InternalEntityFetcher(dsl(), objectMapper);
        var reportEntityFetcher = new ReportEntityFetcher(
                new ValidateAllReport(entityKindStore, internalEntityFetcher, objectMapper));
        var entityFetchers = new EntityFetchers(Set.of(internalEntityFetcher, reportEntityFetcher));
        var jsonPathEvaluator = new JsonPathEvaluator(objectMapper);
        var renderHistoryController = new ViewRenderHistoryController(dsl());
        viewController = new ViewController(dsl(),
                entityStore,
                entityKindStore,
                entityFetchers,
                jsonPathEvaluator,
                new NoopJsEvaluator(),
                ViewCache.noop(),
                renderHistoryController,
                objectMapper);
    }

    @Test
    public void entityOrderMustBePreservedWhenUsingNamePatterns() {
        // create entity kind
        upsert(new MicaKindV1.Builder()
                .name("/test-record-kind")
                .schema(parseObject("""
                        properties:
                          value:
                            type: integer
                        """))
                .build()
                .toPartialEntity(objectMapper));

        // create test records, 2 for each name pattern
        upsert(PartialEntity.create("/first-1", "/test-record-kind", Map.of("value", IntNode.valueOf(1))));
        upsert(PartialEntity.create("/first-2", "/test-record-kind", Map.of("value", IntNode.valueOf(2))));
        upsert(PartialEntity.create("/second-1", "/test-record-kind", Map.of("value", IntNode.valueOf(3))));
        upsert(PartialEntity.create("/second-2", "/test-record-kind", Map.of("value", IntNode.valueOf(4))));
        upsert(PartialEntity.create("/third-1", "/test-record-kind", Map.of("value", IntNode.valueOf(5))));
        upsert(PartialEntity.create("/third-2", "/test-record-kind", Map.of("value", IntNode.valueOf(6))));

        // create view
        upsert(new MicaViewV1.Builder()
                .name("/test-name-patterns")
                .selector(byEntityKind("/test-record-kind")
                        .withNamePatterns(List.of(
                                "/third-.*",
                                "/second-.*",
                                "/first-.*")))
                .data(jsonPath("$"))
                .build()
                .toPartialEntity(objectMapper));

        var result = viewController.getCachedOrRenderAsEntity(RenderViewRequest.of("/test-name-patterns"));
        assertEquals(6, result.data().get("data").size());

        // validate record order
        var values = Streams.stream(result.data().get("data").iterator()).map(it -> it.get("value").asInt())
                .toArray(Integer[]::new);
        assertArrayEquals(new Integer[] { 5, 6, 3, 4, 1, 2 }, values);
    }

    @Test
    public void namePatternShouldSupportSubstitutions() {
        var recordKind = "/test-record-kind-" + System.currentTimeMillis();

        // create entity kind
        upsert(new MicaKindV1.Builder()
                .name(recordKind)
                .schema(parseObject("""
                        properties:
                          value:
                            type: integer
                        """))
                .build()
                .toPartialEntity(objectMapper));

        // create test records
        upsert(PartialEntity.create("/first/record", recordKind, Map.of("value", IntNode.valueOf(1))));
        upsert(PartialEntity.create("/second/record", recordKind, Map.of("value", IntNode.valueOf(2))));
        upsert(PartialEntity.create("/third/record", recordKind, Map.of("value", IntNode.valueOf(3))));

        // create view
        upsert(new MicaViewV1.Builder()
                .name("/test-name-pattern-substitution")
                .parameters(parseObject("""
                        properties:
                          x:
                            type: string
                          y:
                            type: string
                        """))
                .selector(byEntityKind(recordKind)
                        .withNamePatterns(List.of("/${parameters.x}/record", "/${parameters.y}/record")))
                .data(jsonPath("$").withMerge())
                .build()
                .toPartialEntity(objectMapper));

        var result = viewController
                .getCachedOrRenderAsEntity(RenderViewRequest.parameterized("/test-name-pattern-substitution",
                        parameters("x", TextNode.valueOf("first"), "y", TextNode.valueOf("second"))));
        assertEquals(1, result.data().get("data").size());
        assertEquals("/second/record", result.data().get("data").get(0).get("name").asText());
        assertEquals(2, result.data().get("data").get(0).get("value").asInt());
    }

    @Test
    public void viewDataCanBeValidated() {
        // create v1 of the schema
        var recordKindV1 = "/test-kind-v1-" + System.currentTimeMillis();
        upsert(new MicaKindV1.Builder()
                .name(recordKindV1)
                .schema(parseObject("""
                        properties:
                          foo:
                            type: string
                        required: ["foo"]
                        """))
                .build()
                .toPartialEntity(objectMapper));

        // create v2 of the schema in which we replace the "foo" property with "bar"
        var recordKindV2 = "/test-kind-v2-" + System.currentTimeMillis();
        upsert(new MicaKindV1.Builder()
                .name(recordKindV2)
                .schema(parseObject("""
                        properties:
                          bar:
                            type: string
                        required: ["bar"]
                        """))
                .build()
                .toPartialEntity(objectMapper));

        // create test records
        upsert(PartialEntity.create(randomPathPrefix() + "/first/record", recordKindV1,
                Map.of("foo", TextNode.valueOf("1"))));
        upsert(PartialEntity.create(randomPathPrefix() + "/second/record", recordKindV1,
                Map.of("foo", TextNode.valueOf("2"))));

        // create view
        upsert(new MicaViewV1.Builder()
                .name("/test-view-validation")
                .parameters(parseObject("""
                        properties:
                          x:
                            type: string
                        """))
                .selector(byEntityKind(recordKindV1))
                .data(jsonPath("$"))
                .validation(asEntityKind(recordKindV2))
                .build()
                .toPartialEntity(objectMapper));

        // we expect the view to return the original data plus the validation errors
        var result = viewController
                .getCachedOrRenderAsEntity(
                        RenderViewRequest.parameterized("/test-view-validation", NullNode.getInstance()));
        assertEquals(2, result.data().get("data").size());
        assertEquals(2, result.data().get("validation").size());
        assertEquals("required", result.data().get("validation").get(0).get("messages").get(0).get("type").asText());
    }

    @Test
    public void viewsCanBeRenderedAsPropertiesFile() throws Exception {
        // create kind
        var recordKindV1 = "/test-kind-v1-" + System.currentTimeMillis();
        upsert(new MicaKindV1.Builder()
                .name(recordKindV1)
                .schema(parseObject("""
                        properties:
                          data:
                            type: object
                        required: ["data"]
                        """))
                .build()
                .toPartialEntity(objectMapper));

        // create test records
        upsert(PartialEntity.create(randomPathPrefix() + "/first/record", recordKindV1,
                Map.of("data", objectMapper.readTree("""
                        {
                            "x.y": false
                        }
                        """))));
        upsert(PartialEntity.create(randomPathPrefix() + "/second/record", recordKindV1,
                Map.of("data", objectMapper.readTree("""
                        {
                            "x.y": true
                        }
                        """))));

        // create view
        upsert(new MicaViewV1.Builder()
                .name("/test-properties")
                .selector(byEntityKind(recordKindV1))
                .data(jsonPath("$.data"))
                .build()
                .toPartialEntity(objectMapper));

        // we expect entities to be flattened into a .properties format
        var result = viewController.getCachedOrRenderAsProperties(RenderViewRequest.of("/test-properties"));
        assertEquals("x.y=true\n", result);
    }

    @Test
    public void renderingViewsWithUnknownIncludesThrowsError() {
        upsert(new MicaViewV1.Builder()
                .name("/unknown-include")
                .selector(byEntityKind("/acme/foo")
                        .withIncludes(List.of("unknown+what://is/this")))
                .data(jsonPath("$.data"))
                .build()
                .toPartialEntity(objectMapper));

        var error = assertThrows(ApiException.class,
                () -> viewController.getCachedOrRenderAsEntity(RenderViewRequest.of("/unknown-include")));
        assertEquals(CLIENT_ERROR, error.getStatus().getFamily());
        var entity = assertInstanceOf(ApiError.class, error.getResponse().getEntity());
        assertTrue(entity.message().contains("Unsupported URI"));
    }

    @Test
    public void mergeByWithNamePatternsWorkAsIntended() {
        var pathPrefix = randomPathPrefix();
        var kind = pathPrefix + "/test-record-kind";

        // create entity kind
        upsert(new MicaKindV1.Builder()
                .name(kind)
                .schema(parseObject("""
                        properties:
                          value:
                            type: integer
                        """))
                .build()
                .toPartialEntity(objectMapper));

        // create test records, 2 for each name pattern
        upsert(PartialEntity.create(pathPrefix + "/first-1", kind,
                Map.of("key", TextNode.valueOf("foo"), "value", IntNode.valueOf(1))));
        upsert(PartialEntity.create(pathPrefix + "/first-2", kind,
                Map.of("key", TextNode.valueOf("bar"), "value", IntNode.valueOf(2))));
        upsert(PartialEntity.create(pathPrefix + "/second-1", kind,
                Map.of("key", TextNode.valueOf("foo"), "value", IntNode.valueOf(3))));
        upsert(PartialEntity.create(pathPrefix + "/second-2", kind,
                Map.of("key", TextNode.valueOf("bar"), "value", IntNode.valueOf(4))));

        // create view
        var viewName = pathPrefix + "/test-name-patterns";
        upsert(new MicaViewV1.Builder()
                .name(viewName)
                .selector(byEntityKind(kind)
                        .withNamePatterns(List.of(
                                pathPrefix + "/second-.*",
                                pathPrefix + "/first-.*")))
                .data(jsonPath("$").withMergeBy("$.key"))
                .build()
                .toPartialEntity(objectMapper));

        // the entities should be selected in the order of the name patterns:
        // /second-1
        // /second-2
        // /first-1
        // /first-2

        // then merged by $.value

        // we should end up with values 2 and 1

        var result = viewController.getCachedOrRenderAsEntity(RenderViewRequest.of(viewName));
        assertEquals(2, result.data().get("data").size());

        // validate record order
        var values = Streams.stream(result.data().get("data").iterator()).map(it -> it.get("value").asInt())
                .toArray(Integer[]::new);
        assertArrayEquals(new Integer[] { 2, 1 }, values);
    }

    @Test
    public void renderValidateAllReport() {
        var pathPrefix = randomPathPrefix();

        var fooDoc = parseObject("""
                name: %s/foo
                kind: %s/record
                x: 1
                y: 2
                z: 3
                """.formatted(pathPrefix, pathPrefix));

        // create entity kind
        upsert(new MicaKindV1.Builder()
                .name(pathPrefix + "/kind")
                .schema(parseObject("""
                        properties:
                            x:
                              type: number
                            y:
                              type: number
                        """))
                .build()
                .toPartialEntity(objectMapper));

        // create test record

        upsert(PartialEntity.create(pathPrefix + "/first", pathPrefix + "/kind",
                Map.of("x", IntNode.valueOf(1),
                        "y", IntNode.valueOf(2),
                        "z", IntNode.valueOf(3))));

        // create the report view

        upsert(new MicaViewV1.Builder()
                .name(pathPrefix + "/view")
                .selector(byEntityKind(".*")
                        .withIncludes(List.of("mica+report://validateAll?reportUnevaluatedProperties=true")))
                .data(jsonPath("$"))
                .build()
                .toPartialEntity(objectMapper));

        var result = viewController.getCachedOrRenderAsEntity(RenderViewRequest.of(pathPrefix + "/view"));
        assertEquals(1, result.data().get("data").size());
    }

    private static void upsert(PartialEntity entity) {
        dsl().transaction(tx -> entityStore.upsert(tx.dsl(), entity, null).orElseThrow());
    }

    private static String randomPathPrefix() {
        return "/test-" + System.currentTimeMillis();
    }

    private static JsonNode parameters(String k1, JsonNode v1, String k2, JsonNode v2) {
        return objectMapper.convertValue(Map.of(k1, v1, k2, v2), JsonNode.class);
    }

    private static ObjectNode parseObject(@Language("yaml") String s) {
        try {
            return objectMapper.copyWith(new YAMLFactory()).readValue(s, ObjectNode.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
