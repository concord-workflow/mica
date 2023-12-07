package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.schema.ObjectSchemaNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Responsible for loading the initial data set, e.g. the default entity kinds
 * and such.
 */
public class InitialDataLoader {

    private static final Logger log = LoggerFactory.getLogger(InitialDataLoader.class);

    private final BuiltinSchemas builtinSchemas;
    private final EntityStore entityStore;
    private final ObjectMapper objectMapper;

    @Inject
    public InitialDataLoader(BuiltinSchemas builtinSchemas,
                             EntityStore entityStore,
                             ObjectMapper objectMapper) {
        this.builtinSchemas = requireNonNull(builtinSchemas);
        this.entityStore = requireNonNull(entityStore);
        this.objectMapper = requireNonNull(objectMapper);

        // no out of the box support for @PostConstruct in Guice
        load();
    }

    public void load() {
        // built-in entity kinds
        createOrReplace(schema(BuiltinSchemas.MICA_KIND_V1, builtinSchemas.getMicaKindV1Schema()));
        createOrReplace(schema(BuiltinSchemas.MICA_RECORD_V1, builtinSchemas.getMicaRecordV1Schema()));
        createOrReplace(schema(BuiltinSchemas.MICA_VIEW_V1, builtinSchemas.getMicaViewV1Schema()));

        // examples
        createOrReplace(view("/examples/simple/example-view", BuiltinSchemas.MICA_RECORD_V1, "$.data"));
        createOrReplace(record("/examples/simple/example-record-a", TextNode.valueOf("hello!")));
        createOrReplace(record("/examples/simple/example-record-b", TextNode.valueOf("bye!")));
    }

    private void createOrReplace(PartialEntity entity) {
        entityStore.getByName(entity.name())
                .flatMap(existingEntity -> entityStore.deleteById(existingEntity.id()))
                .ifPresent(deleted -> log.info("Removed old version of {}: {}", entity.name(), deleted));

        entityStore.upsert(entity);

        log.info("Created or replaced an entity: {}", entity.name());
    }

    private PartialEntity schema(String name, ObjectSchemaNode schema) {
        return PartialEntity.create(name, BuiltinSchemas.MICA_KIND_V1,
                Map.of("schema", objectMapper.convertValue(schema, JsonNode.class)));
    }

    private PartialEntity view(String name, String selectorEntityKind, String dataJsonPath) {
        return PartialEntity.create(name, BuiltinSchemas.MICA_VIEW_V1,
                Map.of("selector", objectMapper.convertValue(Map.of("entityKind", selectorEntityKind), JsonNode.class),
                        "data", objectMapper.convertValue(Map.of("jsonPath", dataJsonPath), JsonNode.class)));
    }

    private PartialEntity record(String name, JsonNode data) {
        return PartialEntity.create(name, BuiltinSchemas.MICA_RECORD_V1,
                Map.of("data", data));
    }

    private void createOrReplaceView() {

    }
}
