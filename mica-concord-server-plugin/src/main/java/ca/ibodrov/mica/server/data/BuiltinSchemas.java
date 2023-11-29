package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.api.model.ViewLike;
import ca.ibodrov.mica.schema.ObjectSchemaNode;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ca.ibodrov.mica.schema.ObjectSchemaNode.*;
import static ca.ibodrov.mica.server.exceptions.ApiException.ErrorKind.BAD_DATA;

public final class BuiltinSchemas {

    /**
     * MicaRecord/v1 - use to declare entities of any kind.
     */
    public static final String MICA_RECORD_V1 = "MicaRecord/v1";
    public static ObjectSchemaNode MICA_RECORD_V1_SCHEMA = object(Map.of(
            "id", string(),
            "kind", enums(TextNode.valueOf(MICA_RECORD_V1)),
            "name", string(),
            "data", any()),
            Set.of("kind", "name", "data"));

    /**
     * MicaKind/v1 - use to declare new entity kinds.
     */
    public static final String MICA_KIND_V1 = "MicaKind/v1";
    public static final String MICA_KIND_SCHEMA_PROPERTY = "schema";
    public static ObjectSchemaNode MICA_KIND_V1_SCHEMA = object(Map.of(
            "id", string(),
            "kind", enums(TextNode.valueOf(MICA_KIND_V1)),
            "name", string(),
            MICA_KIND_SCHEMA_PROPERTY, any()),
            Set.of("kind", "name", "schema"));

    /**
     * MicaView/v1 - use to declare entity views.
     */
    public static final String MICA_VIEW_V1 = "MicaView/v1";
    public static ObjectSchemaNode MICA_VIEW_V1_SCHEMA = object(Map.of(
            "id", string(),
            "kind", enums(TextNode.valueOf(MICA_VIEW_V1)),
            "name", string(),
            "selector", object(Map.of("entityKind", string()), Set.of("entityKind")),
            "data", object(Map.of("jsonPath", string()), Set.of("jsonPath"))),
            Set.of("kind", "name", "selector", "data"));

    public static ViewLike asView(EntityLike entity) {
        if (!entity.kind().equals(BuiltinSchemas.MICA_VIEW_V1)) {
            throw ApiException.badRequest(BAD_DATA, "Expected a MicaView/v1 entity, got: " + entity.kind());
        }

        var name = entity.name();

        var selectorEntityKind = Optional.ofNullable(entity.data().get("selector"))
                .map(n -> n.get("entityKind"))
                .map(JsonNode::asText)
                .orElseThrow(() -> ApiException.badRequest(BAD_DATA, "View is missing selector.entityKind"));

        var dataJsonPath = Optional.ofNullable(entity.data().get("data"))
                .map(n -> n.get("jsonPath"))
                .map(JsonNode::asText)
                .orElseThrow(() -> ApiException.badRequest(BAD_DATA, "View is missing data.jsonPath"));

        return new ViewLike() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Selector selector() {
                return () -> selectorEntityKind;
            }

            @Override
            public Data data() {
                return () -> dataJsonPath;
            }
        };
    }

    private BuiltinSchemas() {
    }
}
