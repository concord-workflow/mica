package ca.ibodrov.mica.api.model;

import ca.ibodrov.mica.schema.ObjectSchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ViewLike {

    String name();

    Selector selector();

    Data data();

    Optional<? extends Validation> validation();

    Optional<Map<String, ObjectSchemaNode>> parameters();

    interface Selector {

        Optional<List<URI>> includes();

        String entityKind();

        Optional<List<String>> namePatterns();
    }

    interface Data {

        String jsonPath();

        Optional<Boolean> flatten();

        Optional<Boolean> merge();

        Optional<JsonNode> jsonPatch();
    }

    interface Validation {

        String asEntityKind();
    }
}
