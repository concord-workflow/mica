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
        switch (v.getNodeType()) {
            case NULL:
                return ValueType.NULL;
            case BOOLEAN:
                return ValueType.BOOLEAN;
            case NUMBER:
                return ValueType.NUMBER;
            case STRING:
                return ValueType.STRING;
            case OBJECT:
                return ValueType.OBJECT;
            default:
                throw new IllegalArgumentException("Unsupported node type: " + v.getNodeType());
        }
    }
}
