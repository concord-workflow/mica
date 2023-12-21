package ca.ibodrov.mica.server.api;

import ca.ibodrov.mica.api.kinds.MicaKindV1;
import ca.ibodrov.mica.api.kinds.MicaViewV1;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.AbstractDatabaseTest;
import ca.ibodrov.mica.server.UuidGenerator;
import ca.ibodrov.mica.server.api.ViewResource.RenderRequest;
import ca.ibodrov.mica.server.data.BuiltinSchemas;
import ca.ibodrov.mica.server.data.EntityKindStore;
import ca.ibodrov.mica.server.data.EntityStore;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.Streams;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static ca.ibodrov.mica.api.kinds.MicaViewV1.Data.jsonPath;
import static ca.ibodrov.mica.api.kinds.MicaViewV1.Selector.byEntityKind;
import static ca.ibodrov.mica.api.kinds.MicaViewV1.Validation.asEntityKind;
import static ca.ibodrov.mica.schema.ObjectSchemaNode.object;
import static ca.ibodrov.mica.schema.ObjectSchemaNode.string;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ViewResourceTest extends AbstractDatabaseTest {

    private static EntityStore entityStore;
    private static ViewResource viewResource;

    @BeforeAll
    public static void setUp() {
        var uuidGenerator = new UuidGenerator();
        entityStore = new EntityStore(dsl(), objectMapper, uuidGenerator);
        var builtinSchemas = new BuiltinSchemas(objectMapper);
        var entityKindStore = new EntityKindStore(entityStore, builtinSchemas, objectMapper);
        viewResource = new ViewResource(dsl(), entityStore, entityKindStore, objectMapper);
    }

    @Test
    public void validateOrderOfEntitiesWhenUsingNamePatterns() {
        // create entity kind
        entityStore.upsert(new MicaKindV1.Builder()
                .name("/test-record-kind")
                .schema(object(Map.of("value", string()), Set.of("value")))
                .build()
                .toPartialEntity(objectMapper));

        // create test records, 2 for each name pattern
        entityStore.upsert(PartialEntity.create("/first-1", "/test-record-kind", Map.of("value", IntNode.valueOf(1))));
        entityStore.upsert(PartialEntity.create("/first-2", "/test-record-kind", Map.of("value", IntNode.valueOf(2))));
        entityStore.upsert(PartialEntity.create("/second-1", "/test-record-kind", Map.of("value", IntNode.valueOf(3))));
        entityStore.upsert(PartialEntity.create("/second-2", "/test-record-kind", Map.of("value", IntNode.valueOf(4))));
        entityStore.upsert(PartialEntity.create("/third-1", "/test-record-kind", Map.of("value", IntNode.valueOf(5))));
        entityStore.upsert(PartialEntity.create("/third-2", "/test-record-kind", Map.of("value", IntNode.valueOf(6))));

        // create view
        entityStore.upsert(new MicaViewV1.Builder()
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
    public void validateNamePatternSubstitutions() {
        var recordKind = "/test-record-kind-" + System.currentTimeMillis();

        // create entity kind
        entityStore.upsert(new MicaKindV1.Builder()
                .name(recordKind)
                .schema(object(Map.of("value", string()), Set.of("value")))
                .build()
                .toPartialEntity(objectMapper));

        // create test records
        entityStore.upsert(
                PartialEntity.create("/first/record", recordKind, Map.of("value", IntNode.valueOf(1))));
        entityStore.upsert(
                PartialEntity.create("/second/record", recordKind, Map.of("value", IntNode.valueOf(2))));
        entityStore.upsert(
                PartialEntity.create("/third/record", recordKind, Map.of("value", IntNode.valueOf(3))));

        // create view
        entityStore.upsert(new MicaViewV1.Builder()
                .name("/test-name-pattern-substitution")
                .parameters(Map.of("x", string()))
                .selector(byEntityKind(recordKind)
                        .withNamePatterns(List.of("/${x}/record", "/${y}/record")))
                .data(jsonPath("$").withMerge())
                .build()
                .toPartialEntity(objectMapper));

        var result = viewResource.render(RenderRequest.parameterized("/test-name-pattern-substitution",
                Map.of("x", TextNode.valueOf("first"), "y", TextNode.valueOf("second")),
                10));
        assertEquals(1, result.data().get("data").size());
        assertEquals("/second/record", result.data().get("data").get(0).get("name").asText());
        assertEquals(2, result.data().get("data").get(0).get("value").asInt());
    }

    @Test
    public void validateViewData() {
        // create v1 of the schema
        var recordKindV1 = "/test-kind-v1-" + System.currentTimeMillis();
        entityStore.upsert(new MicaKindV1.Builder()
                .name(recordKindV1)
                .schema(object(Map.of("foo", string()), Set.of("foo")))
                .build()
                .toPartialEntity(objectMapper));

        // create v2 of the schema in which we replace the "foo" property with "bar"
        var recordKindV2 = "/test-kind-v2-" + System.currentTimeMillis();
        entityStore.upsert(new MicaKindV1.Builder()
                .name(recordKindV2)
                .schema(object(Map.of("bar", string()), Set.of("bar")))
                .build()
                .toPartialEntity(objectMapper));

        // create test records
        entityStore.upsert(PartialEntity.create("/first/record", recordKindV1, Map.of("foo", TextNode.valueOf("1"))));
        entityStore.upsert(PartialEntity.create("/second/record", recordKindV1, Map.of("foo", TextNode.valueOf("2"))));

        // create view
        entityStore.upsert(new MicaViewV1.Builder()
                .name("/test-view-validation")
                .parameters(Map.of("x", string()))
                .selector(byEntityKind(recordKindV1))
                .data(jsonPath("$"))
                .validation(asEntityKind(recordKindV2))
                .build()
                .toPartialEntity(objectMapper));

        // we expect the view to return the original data plus the validation errors
        var result = viewResource.render(RenderRequest.parameterized("/test-view-validation", Map.of(), 10));
        assertEquals(2, result.data().get("data").size());
        assertEquals(2, result.data().get("validation").size());
        assertEquals("MISSING_PROPERTY",
                result.data().get("validation").get(0).get("properties").get("bar").get("error").get("kind").asText());
        assertEquals("MISSING_PROPERTY",
                result.data().get("validation").get(1).get("properties").get("bar").get("error").get("kind").asText());
    }
}
