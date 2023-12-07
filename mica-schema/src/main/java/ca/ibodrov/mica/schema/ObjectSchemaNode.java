package ca.ibodrov.mica.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

import static ca.ibodrov.mica.schema.ValueType.*;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_ABSENT;

/**
 * @implNote When adding fields, make sure to update
 *           {@link #fromObjectNode(ObjectNode)}.
 */
@JsonInclude(NON_ABSENT)
public record ObjectSchemaNode(Optional<String> type,
        Optional<Map<String, ObjectSchemaNode>> properties,
        Optional<Set<String>> required,
        @JsonProperty("enum") Optional<List<JsonNode>> enumeratedValues,
        Optional<ObjectSchemaNode> items,
        Optional<JsonNode> additionalProperties,
        @JsonProperty("$ref") Optional<String> ref) {

    public static ObjectSchemaNode ref(String ref) {
        return new Builder()
                .ref(ref)
                .build();
    }

    public static ObjectSchemaNode array(ObjectSchemaNode items) {
        return new Builder()
                .type(ARRAY.key())
                .items(items)
                .build();
    }

    public static ObjectSchemaNode object(Map<String, ObjectSchemaNode> properties, Set<String> required) {
        return new Builder()
                .type(OBJECT.key())
                .properties(properties)
                .required(required)
                .build();
    }

    public static ObjectSchemaNode string() {
        return new Builder()
                .type(STRING.key())
                .build();
    }

    public static ObjectSchemaNode bool() {
        return new Builder()
                .type(BOOLEAN.key())
                .build();
    }

    public static ObjectSchemaNode enums(JsonNode... values) {
        return new Builder()
                .type(STRING.key())
                .enums(List.of(values))
                .build();
    }

    public static ObjectSchemaNode any() {
        return new Builder()
                .type(ANY.key())
                .build();
    }

    public static ObjectSchemaNode fromObjectNode(ObjectNode node) {
        var builder = new Builder();

        Optional.ofNullable(node.get("type"))
                .map(JsonNode::asText)
                .ifPresent(builder::type);

        Optional.ofNullable(node.get("properties"))
                .map(JsonNode::fields)
                .ifPresent(fields -> {
                    var properties = new HashMap<String, ObjectSchemaNode>();
                    fields.forEachRemaining(entry -> {
                        var name = entry.getKey();
                        var value = entry.getValue();
                        if (value.isObject()) {
                            properties.put(name, fromObjectNode((ObjectNode) value));
                        } else {
                            throw new IllegalArgumentException("property '" + name + "' must be an object");
                        }
                    });
                    builder.properties(properties);
                });

        Optional.ofNullable(node.get("required"))
                .map(JsonNode::elements)
                .ifPresent(elements -> {
                    var required = new HashSet<String>();
                    elements.forEachRemaining(element -> {
                        if (element.isTextual()) {
                            required.add(element.asText());
                        } else {
                            throw new IllegalArgumentException("'required' must be an array of strings");
                        }
                    });
                    builder.required(required);
                });

        Optional.ofNullable(node.get("enum"))
                .map(JsonNode::elements)
                .ifPresent(elements -> {
                    var enums = new ArrayList<JsonNode>();
                    elements.forEachRemaining(enums::add);
                    builder.enums(enums);
                });

        Optional.ofNullable(node.get("items"))
                .filter(JsonNode::isObject)
                .map(ObjectNode.class::cast)
                .ifPresent(items -> builder.items(fromObjectNode(items)));

        Optional.ofNullable(node.get("additionalProperties"))
                .ifPresent(builder::additionalProperties);

        Optional.ofNullable(node.get("$ref"))
                .map(JsonNode::asText)
                .ifPresent(builder::ref);

        return builder.build();
    }

    public static class Builder {

        private Optional<String> type = Optional.empty();
        private Optional<Map<String, ObjectSchemaNode>> properties = Optional.empty();
        private Optional<Set<String>> required = Optional.empty();
        private Optional<List<JsonNode>> enumeratedValues = Optional.empty();
        private Optional<ObjectSchemaNode> items = Optional.empty();
        private Optional<JsonNode> additionalProperties = Optional.empty();
        private Optional<String> ref = Optional.empty();

        public Builder type(ValueType type) {
            return this.type(type.key());
        }

        public Builder type(String type) {
            this.type = Optional.of(type);
            return this;
        }

        public Builder properties(Map<String, ObjectSchemaNode> properties) {
            this.properties = Optional.of(properties);
            return this;
        }

        public Builder required(Set<String> required) {
            this.required = Optional.of(required);
            return this;
        }

        public Builder enums(List<JsonNode> enumeratedValues) {
            this.enumeratedValues = Optional.of(enumeratedValues);
            return this;
        }

        public Builder items(ObjectSchemaNode items) {
            this.items = Optional.of(items);
            return this;
        }

        public Builder additionalProperties(JsonNode additionalProperties) {
            this.additionalProperties = Optional.of(additionalProperties);
            return this;
        }

        public Builder ref(String ref) {
            this.ref = Optional.of(ref);
            return this;
        }

        public ObjectSchemaNode build() {
            return new ObjectSchemaNode(type, properties, required, enumeratedValues, items, additionalProperties, ref);
        }
    }
}
