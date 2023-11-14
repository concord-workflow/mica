package ca.ibodrov.mica.schema;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ca.ibodrov.mica.schema.StandardTypes.OBJECT_TYPE;

public record ObjectSchemaNode(Optional<String> type,
        Optional<Map<String, ObjectSchemaNode>> properties,
        Optional<Set<String>> required) {

    public static ObjectSchemaNode emptyObject() {
        return new ObjectSchemaNode(Optional.of(OBJECT_TYPE), Optional.empty(), Optional.empty());
    }

    public Optional<ObjectSchemaNode> getProperty(String name) {
        return properties().flatMap(p -> Optional.ofNullable(p.get(name)));
    }
}
