package ca.ibodrov.mica.schema;

import com.fasterxml.jackson.databind.JsonNode;

public enum ValueType {

    ANY("any"),
    ARRAY("array"),
    BOOLEAN("boolean"),
    NUMBER("number"),
    OBJECT("object"),
    STRING("string"),
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
        switch (v.getNodeType()) {
            case ARRAY:
                return ValueType.ARRAY;
            case BOOLEAN:
                return ValueType.BOOLEAN;
            case NUMBER:
                return ValueType.NUMBER;
            case OBJECT:
                return ValueType.OBJECT;
            case STRING:
                return ValueType.STRING;
            case NULL:
                return ValueType.NULL;
            default:
                throw new IllegalArgumentException("Unsupported node type: " + v.getNodeType());
        }
    }
}
