package ca.ibodrov.mica.api.model;

import ca.ibodrov.mica.schema.ObjectSchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Optional;

public interface ViewLike {

    String name();

    Selector selector();

    Data data();

    Optional<Map<String, ObjectSchemaNode>> parameters();

    interface Selector {

        String entityKind();
    }

    interface Data {

        String jsonPath();

        Optional<JsonNode> jsonPatch();

        Optional<Boolean> flatten();

        Optional<Boolean> merge();
    }
}
