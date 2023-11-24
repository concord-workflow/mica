package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.schema.ObjectSchemaNode;

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
            "kind", constString(MICA_RECORD_V1),
            "name", string(),
            "data", any()),
            Set.of("name", "kind", "data"));

    /**
     * MicaKind/v1 - use to declare new entity kinds.
     */
    public static final String MICA_KIND_V1 = "MicaKind/v1";

    public static ObjectSchemaNode MICA_KIND_V1_SCHEMA = object(Map.of(
            "id", string(),
            "kind", constString(MICA_KIND_V1),
            "name", string(),
            "extendsKind", string(),
            "schema", any()),
            Set.of("name", "kind", "schema"));

    private BuiltinSchemas() {
    }
}
