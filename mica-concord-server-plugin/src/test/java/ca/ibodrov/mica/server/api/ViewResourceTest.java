package ca.ibodrov.mica.server.api;

import ca.ibodrov.mica.api.kinds.MicaKindV1;
import ca.ibodrov.mica.api.kinds.MicaViewV1;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.api.model.RenderRequest;
import ca.ibodrov.mica.server.AbstractDatabaseTest;
import ca.ibodrov.mica.server.UuidGenerator;
import ca.ibodrov.mica.server.data.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Streams;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static ca.ibodrov.mica.api.kinds.MicaViewV1.Data.jsonPath;
import static ca.ibodrov.mica.api.kinds.MicaViewV1.Selector.byEntityKind;
import static ca.ibodrov.mica.api.kinds.MicaViewV1.Validation.asEntityKind;
import static ca.ibodrov.mica.server.data.UserEntryUtils.user;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ViewResourceTest extends AbstractDatabaseTest {

    private static final UserPrincipal session = new UserPrincipal("test", user("test"));
    private static EntityStore entityStore;
    private static ViewResource viewResource;

    @BeforeAll
    public static void setUp() {
        var uuidGenerator = new UuidGenerator();
        var entityHistoryController = new EntityHistoryController(dsl());
        entityStore = new EntityStore(dsl(), objectMapper, uuidGenerator, entityHistoryController);
        var builtinSchemas = new BuiltinSchemas(objectMapper);
        var entityKindStore = new EntityKindStore(entityStore, builtinSchemas);
        var entityFetchers = Set.<EntityFetcher>of(new InternalEntityFetcher(dsl(), objectMapper));
        viewResource = new ViewResource(dsl(), entityStore, entityKindStore, entityFetchers, objectMapper);
    }

    @Test
    public void entityOrderMustBePreservedWhenUsingNamePatterns() {
        // create entity kind
        entityStore.upsert(session, new MicaKindV1.Builder()
                .name("/test-record-kind")
                .schema(parseObject("""
                        properties:
                          value:
                            type: integer
                        """))
                .build()
                .toPartialEntity(objectMapper));

        // create test records, 2 for each name pattern
        entityStore.upsert(session,
                PartialEntity.create("/first-1", "/test-record-kind", Map.of("value", IntNode.valueOf(1))));
        entityStore.upsert(session,
                PartialEntity.create("/first-2", "/test-record-kind", Map.of("value", IntNode.valueOf(2))));
        entityStore.upsert(session,
                PartialEntity.create("/second-1", "/test-record-kind", Map.of("value", IntNode.valueOf(3))));
        entityStore.upsert(session,
                PartialEntity.create("/second-2", "/test-record-kind", Map.of("value", IntNode.valueOf(4))));
        entityStore.upsert(session,
                PartialEntity.create("/third-1", "/test-record-kind", Map.of("value", IntNode.valueOf(5))));
        entityStore.upsert(session,
                PartialEntity.create("/third-2", "/test-record-kind", Map.of("value", IntNode.valueOf(6))));

        // create view
        entityStore.upsert(session, new MicaViewV1.Builder()
                .name("/test-name-patterns")
                .selector(byEntityKind("/test-record-kind")
                        .withNamePatterns(List.of(
                                "/first-.*",
                                "/second-.*",
                                "/third-.*")))
                .data(jsonPath("$"))
                .build()
                .toPartialEntity(objectMapper));

        var result = viewResource.render(RenderRequest.of("/test-name-patterns", 10));
        assertEquals(6, result.data().get("data").size());

        // validate record order
        var values = Streams.stream(result.data().get("data").iterator()).map(it -> it.get("value").asInt())
                .toArray(Integer[]::new);
        assertArrayEquals(new Integer[] { 1, 2, 3, 4, 5, 6 }, values);
    }

    @Test
    public void namePatternShouldSupportSubstitutions() {
        var recordKind = "/test-record-kind-" + System.currentTimeMillis();

        // create entity kind
        entityStore.upsert(session, new MicaKindV1.Builder()
                .name(recordKind)
                .schema(parseObject("""
                        properties:
                          value:
                            type: integer
                        """))
                .build()
                .toPartialEntity(objectMapper));

        // create test records
        entityStore.upsert(session,
                PartialEntity.create("/first/record", recordKind, Map.of("value", IntNode.valueOf(1))));
        entityStore.upsert(session,
                PartialEntity.create("/second/record", recordKind, Map.of("value", IntNode.valueOf(2))));
        entityStore.upsert(session,
                PartialEntity.create("/third/record", recordKind, Map.of("value", IntNode.valueOf(3))));

        // create view
        entityStore.upsert(session, new MicaViewV1.Builder()
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

        var result = viewResource.render(RenderRequest.parameterized("/test-name-pattern-substitution",
                parameters("x", TextNode.valueOf("first"), "y", TextNode.valueOf("second")),
                10));
        assertEquals(1, result.data().get("data").size());
        assertEquals("/second/record", result.data().get("data").get(0).get("name").asText());
        assertEquals(2, result.data().get("data").get(0).get("value").asInt());
    }

    @Test
    public void viewDataCanBeValidated() {
        // create v1 of the schema
        var recordKindV1 = "/test-kind-v1-" + System.currentTimeMillis();
        entityStore.upsert(session, new MicaKindV1.Builder()
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
        entityStore.upsert(session, new MicaKindV1.Builder()
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
        entityStore.upsert(session, PartialEntity.create(randomPathPrefix() + "/first/record", recordKindV1,
                Map.of("foo", TextNode.valueOf("1"))));
        entityStore.upsert(session, PartialEntity.create(randomPathPrefix() + "/second/record", recordKindV1,
                Map.of("foo", TextNode.valueOf("2"))));

        // create view
        entityStore.upsert(session, new MicaViewV1.Builder()
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
        var result = viewResource
                .render(RenderRequest.parameterized("/test-view-validation", NullNode.getInstance(), 10));
        assertEquals(2, result.data().get("data").size());
        assertEquals(2, result.data().get("validation").size());
        assertEquals("required", result.data().get("validation").get(0).get("messages").get(0).get("type").asText());
    }

    @Test
    public void viewsCanBeRenderedAsPropertiesFile() throws Exception {
        // create kind
        var recordKindV1 = "/test-kind-v1-" + System.currentTimeMillis();
        entityStore.upsert(session, new MicaKindV1.Builder()
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
        entityStore.upsert(session, PartialEntity.create(randomPathPrefix() + "/first/record", recordKindV1,
                Map.of("data", objectMapper.readTree("""
                        {
                            "x.y": false
                        }
                        """))));
        entityStore.upsert(session, PartialEntity.create(randomPathPrefix() + "/second/record", recordKindV1,
                Map.of("data", objectMapper.readTree("""
                        {
                            "x.y": true
                        }
                        """))));

        // create view
        entityStore.upsert(session, new MicaViewV1.Builder()
                .name("/test-properties")
                .selector(byEntityKind(recordKindV1))
                .data(jsonPath("$.data"))
                .build()
                .toPartialEntity(objectMapper));

        // we expect entities to be flattened into a .properties format
        var result = viewResource.renderProperties(RenderRequest.of("/test-properties", 10));
        assertEquals("x.y=true\n", result);
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
