package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import javax.inject.Inject;
import java.util.Optional;

import static ca.ibodrov.mica.api.kinds.MicaKindV1.MICA_KIND_V1;
import static ca.ibodrov.mica.api.kinds.MicaKindV1.SCHEMA_PROPERTY;
import static ca.ibodrov.mica.server.data.BuiltinSchemas.STANDARD_PROPERTIES_REF;
import static java.util.Objects.requireNonNull;

/**
 * @implNote entity kinds are regular entities with {@code kind=/mica/kind/v1}.
 */
public class EntityKindStore {

    private final EntityStore entityStore;
    private final BuiltinSchemas builtinSchemas;

    @Inject
    public EntityKindStore(EntityStore entityStore,
                           BuiltinSchemas builtinSchemas) {

        this.entityStore = requireNonNull(entityStore);
        this.builtinSchemas = requireNonNull(builtinSchemas);
    }

    public boolean isKindExists(String kind) {
        return entityStore.isNameAndKindExists(kind, MICA_KIND_V1);
    }

    /**
     * Returns a schema for the given entity kind. The schema includes both standard
     * properties (see {@link BuiltinSchemas#getStandardProperties()}) and custom
     * properties defined in the entity kind.
     */
    public Optional<JsonNode> getSchemaForKind(String kind) {
        assert kind != null;
        return builtinSchemas.get(kind)
                .or(() -> entityStore.getByName(kind)
                        .map(EntityKindStore::assertKind)
                        .flatMap(e -> Optional.ofNullable(e.getProperty(SCHEMA_PROPERTY)))
                        .map(EntityKindStore::addStandardPropertiesRef));
    }

    private static <T extends EntityLike> T assertKind(T entity) {
        if (!MICA_KIND_V1.equals(entity.kind())) {
            throw new StoreException("Expected a %s entity, got something else. Entity '%s' is a %s"
                    .formatted(MICA_KIND_V1, entity.name(), entity.kind()));
        }
        return entity;
    }

    private static JsonNode addStandardPropertiesRef(JsonNode schema) {
        if (!schema.isObject()) {
            return schema;
        }
        var ref = schema.get("ref");
        if (ref != null && !ref.isNull()) {
            return schema;
        }
        ((ObjectNode) schema).set("$ref", TextNode.valueOf(STANDARD_PROPERTIES_REF));
        return schema;
    }
}
