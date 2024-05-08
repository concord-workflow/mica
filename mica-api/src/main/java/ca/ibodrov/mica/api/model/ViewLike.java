package ca.ibodrov.mica.api.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ViewLike {

    String name();

    Selector selector();

    Data data();

    Optional<? extends Validation> validation();

    Optional<JsonNode> parameters();

    interface Selector {

        Optional<List<String>> includes();

        String entityKind();

        Optional<List<String>> namePatterns();
    }

    interface Data {

        JsonNode jsonPath();

        Optional<Boolean> flatten();

        Optional<Boolean> merge();

        Optional<JsonNode> jsonPatch();

        Optional<List<String>> dropProperties();

        Optional<Map<String, JsonNode>> map();
    }

    interface Validation {

        String asEntityKind();
    }
}
