package ca.ibodrov.mica.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ca.ibodrov.mica.schema.StandardTypes.*;

public record ObjectSchemaNode(Optional<String> type,
        Optional<Map<String, ObjectSchemaNode>> properties,
        Optional<Set<String>> required,
        @JsonProperty("const") JsonNode constant) {

    private static final JsonNode NULL_CONSTANT = NullNode.getInstance();

    public static ObjectSchemaNode emptyObject() {
        return new ObjectSchemaNode(Optional.of(OBJECT_TYPE), Optional.empty(), Optional.empty(), NULL_CONSTANT);
    }

    public static ObjectSchemaNode object(Map<String, ObjectSchemaNode> properties, Set<String> required) {
        return new ObjectSchemaNode(Optional.of(OBJECT_TYPE), Optional.of(properties), Optional.of(required),
                NULL_CONSTANT);
    }

    public static ObjectSchemaNode string() {
        return new ObjectSchemaNode(Optional.of(STRING_TYPE), Optional.empty(), Optional.empty(), NULL_CONSTANT);
    }

    public static ObjectSchemaNode constString(String value) {
        return new ObjectSchemaNode(Optional.of(STRING_TYPE), Optional.empty(), Optional.empty(),
                TextNode.valueOf(value));
    }

    public static ObjectSchemaNode any() {
        return new ObjectSchemaNode(Optional.of(ANY_TYPE), Optional.empty(), Optional.empty(), NULL_CONSTANT);
    }

    public Optional<ObjectSchemaNode> getProperty(String name) {
        return properties().flatMap(p -> Optional.ofNullable(p.get(name)));
    }
}
