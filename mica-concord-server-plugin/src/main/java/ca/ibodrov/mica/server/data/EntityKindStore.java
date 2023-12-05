package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityVersion;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.schema.ObjectSchemaNode;
import ca.ibodrov.mica.schema.ValueType;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.inject.Inject;
import java.util.Optional;

import static ca.ibodrov.mica.schema.ValueType.OBJECT;
import static ca.ibodrov.mica.server.data.BuiltinSchemas.MICA_KIND_SCHEMA_PROPERTY;
import static java.util.Objects.requireNonNull;

/**
 * @implNote entity kinds are regular entities with {@code kind=/mica/kind/v1}.
 */
public class EntityKindStore {

    private final EntityStore entityStore;
    private final ObjectMapper objectMapper;

    @Inject
    public EntityKindStore(EntityStore entityStore, ObjectMapper objectMapper) {
        this.entityStore = requireNonNull(entityStore);
        this.objectMapper = requireNonNull(objectMapper);
    }

    public boolean isKindExists(String kind) {
        return entityStore.isNameAndKindExists(kind, BuiltinSchemas.MICA_KIND_V1);
    }

    public Optional<ObjectSchemaNode> getSchemaForKind(String kind) {
        return entityStore.getByName(kind)
                .map(EntityKindStore::assertKind)
                .flatMap(e -> Optional.ofNullable(e.getProperty(MICA_KIND_SCHEMA_PROPERTY)))
                .map(v -> objectMapper.convertValue(v, ObjectSchemaNode.class))
                .map(EntityKindStore::sanityCheck);
    }

    public Optional<EntityVersion> upsert(PartialEntity entity) {
        return entityStore.upsert(assertKind(entity));
    }

    private static <T extends EntityLike> T assertKind(T entity) {
        if (!BuiltinSchemas.MICA_KIND_V1.equals(entity.kind())) {
            throw new StoreException("Expected a %s entity, got something else. Entity '%s' is a %s"
                    .formatted(BuiltinSchemas.MICA_KIND_V1, entity.name(), entity.kind()));
        }
        return entity;
    }

    private static ObjectSchemaNode sanityCheck(ObjectSchemaNode schema) {
        if (schema.type().map(ValueType::ofKey).orElse(OBJECT).equals(OBJECT)
                && schema.enumeratedValues().isEmpty()
                && schema.properties().isEmpty()) {
            throw new StoreException("Schema cannot be used, it doesn't have any properties or enum values: " + schema);
        }
        return schema;
    }
}
