package ca.ibodrov.mica.schema;

import com.fasterxml.jackson.databind.JsonNode;

public enum ValueType {

    ANY("any"),
    OBJECT("object"),
    STRING("string"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    NULL("null");

    private final String key;

    ValueType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static ValueType ofKey(String key) {
        for (ValueType t : values()) {
            if (t.key.equals(key)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unsupported ValueType key: " + key);
    }

    public static ValueType typeOf(JsonNode v) {
        if (v.isTextual()) {
            return ValueType.STRING;
        } else if (v.isNumber()) {
            return ValueType.NUMBER;
        } else if (v.isObject()) {
            return ValueType.OBJECT;
        } else {
            throw new IllegalArgumentException("Unsupported 'enum' node type: " + v.getNodeType());
        }
    }
}
