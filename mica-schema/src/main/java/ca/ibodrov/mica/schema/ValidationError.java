package ca.ibodrov.mica.schema;

import java.util.Map;

public record ValidationError(Kind kind, Map<String, String> metadata) {

    public enum Kind {
        INVALID_VALUE,
        MISSING_PROPERTY,
        INVALID_TYPE,
        INVALID_SCHEMA
    }
}
