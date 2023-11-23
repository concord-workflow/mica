package ca.ibodrov.mica.schema;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public record ValidationError(Kind kind, Map<String, JsonNode> metadata) {

    public enum Kind {
        INVALID_VALUE,
        MISSING_PROPERTY,
        INVALID_TYPE,
        INVALID_SCHEMA,
        UNEXPECTED_VALUE
    }
}
