package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.Entity;
import ca.ibodrov.mica.api.model.EntityVersion;
import ca.ibodrov.mica.api.model.PartialEntity;
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
        return entityStore.getByNameAndKind(kind, BuiltinSchemas.MICA_KIND_V1).map(this::intoSchema);
    }

    public Optional<EntityVersion> upsert(PartialEntity entity) {
        assertKind(entity);
        return entityStore.upsert(entity);
    }

    private ObjectSchemaNode intoSchema(Entity entity) {
        return objectMapper.convertValue(entity.data(), ObjectSchemaNode.class);
    }

    private static void assertKind(PartialEntity entity) {
        if (!BuiltinSchemas.MICA_KIND_V1.equals(entity.kind())) {
            throw new IllegalArgumentException("Only %s kind is allowed for entity kind definitions"
                    .formatted(BuiltinSchemas.MICA_KIND_V1));
        }
    }
}
