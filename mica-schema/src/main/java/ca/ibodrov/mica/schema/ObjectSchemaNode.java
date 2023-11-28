package ca.ibodrov.mica.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ca.ibodrov.mica.schema.StandardTypes.*;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_ABSENT;

@JsonInclude(NON_ABSENT)
public record ObjectSchemaNode(Optional<String> type,
        Optional<Map<String, ObjectSchemaNode>> properties,
        Optional<Set<String>> required,
        @JsonProperty("enum") Optional<List<JsonNode>> enumeratedValues) {

    public static ObjectSchemaNode object(Map<String, ObjectSchemaNode> properties, Set<String> required) {
        return new ObjectSchemaNode(Optional.of(OBJECT_TYPE), Optional.of(properties), Optional.of(required),
                Optional.empty());
    }

    public static ObjectSchemaNode string() {
        return new ObjectSchemaNode(Optional.of(STRING_TYPE), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static ObjectSchemaNode enums(JsonNode... values) {
        return new ObjectSchemaNode(Optional.of(STRING_TYPE), Optional.empty(), Optional.empty(),
                Optional.of(List.of(values)));
    }

    public static ObjectSchemaNode any() {
        return new ObjectSchemaNode(Optional.of(ANY_TYPE), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public Optional<ObjectSchemaNode> getProperty(String name) {
        return properties().flatMap(p -> Optional.ofNullable(p.get(name)));
    }
}
