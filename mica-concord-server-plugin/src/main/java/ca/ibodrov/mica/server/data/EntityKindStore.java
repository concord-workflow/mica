package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.Entity;
import ca.ibodrov.mica.api.model.EntityVersion;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.api.model.WithMetadata;
import ca.ibodrov.mica.schema.ObjectSchemaNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.inject.Inject;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * @implNote entity kinds are regular entities with {@code kind=MicaKind/v1}.
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
        var entity = entityStore.getByName(kind);
        entity.ifPresent(EntityKindStore::assertKind);
        return entity.map(this::intoSchema);
    }

    public Optional<EntityVersion> upsert(PartialEntity entity) {
        assertKind(entity);
        return entityStore.upsert(entity);
    }

    private ObjectSchemaNode intoSchema(Entity entity) {
        return objectMapper.convertValue(entity.data(), ObjectSchemaNode.class);
    }

    private static void assertKind(WithMetadata entity) {
        if (!BuiltinSchemas.MICA_KIND_V1.equals(entity.kind())) {
            throw new IllegalArgumentException("Expected a %s entity, got something else. Entity '%s' is a %s"
                    .formatted(BuiltinSchemas.MICA_KIND_V1, entity.name(), entity.kind()));
        }
    }
}
