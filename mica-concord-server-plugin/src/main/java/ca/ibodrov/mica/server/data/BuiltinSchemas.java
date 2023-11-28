package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.schema.ObjectSchemaNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Map;
import java.util.Set;

import static ca.ibodrov.mica.schema.ObjectSchemaNode.*;

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
    public static ObjectSchemaNode MICA_KIND_V1_SCHEMA = object(Map.of(
            "id", string(),
            "kind", enums(TextNode.valueOf(MICA_KIND_V1)),
            "name", string(),
            "extendsKind", string(),
            "schema", any()),
            Set.of("kind", "name", "schema"));

    /**
     * MicaEntityView/v1 - use to declare entity views.
     */
    public static final String MICA_ENTITY_VIEW_V1 = "MicaEntityView/v1";
    public static ObjectSchemaNode MICA_ENTITY_VIEW_V1_SCHEMA = object(Map.of(
            "id", string(),
            "kind", enums(TextNode.valueOf(MICA_ENTITY_VIEW_V1)),
            "name", string(),
            "selector", object(Map.of("kind", string()), Set.of("kind")),
            "fields", any()),
            Set.of("kind", "name", "selector", "fields"));

    private BuiltinSchemas() {
    }
}
