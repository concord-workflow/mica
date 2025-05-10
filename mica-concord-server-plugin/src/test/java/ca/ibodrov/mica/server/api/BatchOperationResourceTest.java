package ca.ibodrov.mica.server.api;

import ca.ibodrov.mica.api.model.EntityVersion;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.AbstractDatabaseTest;
import ca.ibodrov.mica.server.YamlMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ca.ibodrov.mica.api.model.BatchOperationRequest.deleteByNamePatterns;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BatchOperationResourceTest extends AbstractDatabaseTest {

    private static YamlMapper yamlMapper;
    private static BatchOperationResource batchOperationResource;

    @BeforeAll
    public static void setUp() {
        yamlMapper = new YamlMapper(objectMapper);
        batchOperationResource = new BatchOperationResource(entityStore);
    }

    private static EntityVersion upsert(PartialEntity entity) {
        return dsl().transactionResult(tx -> entityStore.upsert(tx.dsl(), entity, null).orElseThrow());
    }

    @Test
    public void batchDeleteWithNamePatterns() {
        var fooEntity = parseYaml("""
                kind: /mica/record/v1
                name: /foo
                data: "foo!"
                """);
        var fooVersion1 = upsert(fooEntity);

        var barEntity = parseYaml("""
                kind: /mica/record/v1
                name: /bar
                data: "bar!"
                """);
        var barVersion1 = upsert(barEntity);

        var bazEntity = parseYaml("""
                kind: /mica/record/v1
                name: /baz
                data: "baz!"
                """);
        var bazVersion1 = upsert(bazEntity);

        // delete "/foo" and "/bar", but not "/baz"
        var result = batchOperationResource.apply(deleteByNamePatterns(List.of("/foo", "/bar")));

        // check the results
        var deletedEntities = result.deletedEntities().orElseThrow();
        assertEquals(2, deletedEntities.size());
        assertEquals("/foo", deletedEntities.get(0).name());
        assertEquals(fooVersion1.id(), deletedEntities.get(0).id());
        assertEquals("/bar", deletedEntities.get(1).name());
        assertEquals(barVersion1.id(), deletedEntities.get(1).id());

        // "/foo" and "/bar" should be gone, but "/baz" should still be there
        assertTrue(entityStore.getById(fooVersion1.id()).isEmpty());
        assertTrue(entityStore.getById(barVersion1.id()).isEmpty());
        assertTrue(entityStore.getById(bazVersion1.id()).isPresent());

        // add "/foo" and "/bar" back
        var fooVersion2 = upsert(fooEntity);
        var barVersion2 = upsert(barEntity);

        // delete all "/ba.*" entities
        result = batchOperationResource.apply(deleteByNamePatterns(List.of("/ba.*")));

        // check the results
        deletedEntities = result.deletedEntities().orElseThrow();
        assertEquals(2, deletedEntities.size());
        assertEquals("/baz", deletedEntities.get(0).name());
        assertEquals(bazVersion1.id(), deletedEntities.get(0).id());
        assertEquals("/bar", deletedEntities.get(1).name());
        assertEquals(barVersion2.id(), deletedEntities.get(1).id());

        // check that /foo is still there
        assertTrue(entityStore.getById(fooVersion2.id()).isPresent());
    }

    private static PartialEntity parseYaml(@Language("yaml") String yaml) {
        try {
            return yamlMapper.readValue(yaml, PartialEntity.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
