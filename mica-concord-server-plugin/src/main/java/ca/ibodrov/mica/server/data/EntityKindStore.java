package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.schema.ObjectSchemaNode;
import ca.ibodrov.mica.schema.ValueType;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.inject.Inject;
import java.util.Optional;

import static ca.ibodrov.mica.api.kinds.MicaKindV1.MICA_KIND_V1;
import static ca.ibodrov.mica.api.kinds.MicaKindV1.SCHEMA_PROPERTY;
import static ca.ibodrov.mica.schema.ValueType.OBJECT;
import static java.util.Objects.requireNonNull;

/**
 * @implNote entity kinds are regular entities with {@code kind=/mica/kind/v1}.
 */
public class EntityKindStore {

    private final EntityStore entityStore;
    private final BuiltinSchemas builtinSchemas;
    private final ObjectMapper objectMapper;

    @Inject
    public EntityKindStore(EntityStore entityStore,
                           BuiltinSchemas builtinSchemas,
                           ObjectMapper objectMapper) {

        this.entityStore = requireNonNull(entityStore);
        this.builtinSchemas = requireNonNull(builtinSchemas);
        this.objectMapper = requireNonNull(objectMapper);
    }

    public boolean isKindExists(String kind) {
        return entityStore.isNameAndKindExists(kind, MICA_KIND_V1);
    }

    public Optional<ObjectSchemaNode> getSchemaForKind(String kind) {
        assert kind != null;
        return builtinSchemas.get(kind)
                .or(() -> entityStore.getByName(kind)
                        .map(EntityKindStore::assertKind)
                        .flatMap(e -> Optional.ofNullable(e.getProperty(SCHEMA_PROPERTY)))
                        .map(v -> objectMapper.convertValue(v, ObjectSchemaNode.class))
                        .map(EntityKindStore::sanityCheck));
    }

    private static <T extends EntityLike> T assertKind(T entity) {
        if (!MICA_KIND_V1.equals(entity.kind())) {
            throw new StoreException("Expected a %s entity, got something else. Entity '%s' is a %s"
                    .formatted(MICA_KIND_V1, entity.name(), entity.kind()));
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
