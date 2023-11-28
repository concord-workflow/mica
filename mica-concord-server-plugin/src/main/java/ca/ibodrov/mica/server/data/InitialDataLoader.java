package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.schema.ObjectSchemaNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final EntityKindStore entityKindStore;
    private final ObjectMapper objectMapper;

    @Inject
    public InitialDataLoader(EntityKindStore entityKindStore, ObjectMapper objectMapper) {
        this.entityKindStore = requireNonNull(entityKindStore);
        this.objectMapper = requireNonNull(objectMapper);

        // no out of the box support for @PostConstruct in Guice
        load();
    }

    public void load() {
        insertIfNotExists(BuiltinSchemas.MICA_KIND_V1, BuiltinSchemas.MICA_KIND_V1_SCHEMA);
        insertIfNotExists(BuiltinSchemas.MICA_RECORD_V1, BuiltinSchemas.MICA_RECORD_V1_SCHEMA);
        insertIfNotExists(BuiltinSchemas.MICA_ENTITY_VIEW_V1, BuiltinSchemas.MICA_ENTITY_VIEW_V1_SCHEMA);
    }

    private void insertIfNotExists(String name, ObjectSchemaNode schema) {
        if (entityKindStore.isKindExists(name)) {
            return;
        }
        entityKindStore.upsert(PartialEntity.create(name, BuiltinSchemas.MICA_KIND_V1,
                Map.of("schema", objectMapper.convertValue(schema, JsonNode.class))));
        log.info("Inserted an entity kind: {}", name);
    }
}
