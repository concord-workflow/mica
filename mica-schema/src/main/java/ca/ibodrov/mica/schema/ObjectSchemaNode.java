package ca.ibodrov.mica.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ca.ibodrov.mica.schema.ValueType.*;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_ABSENT;

@JsonInclude(NON_ABSENT)
public record ObjectSchemaNode(Optional<String> type,
        Optional<Map<String, ObjectSchemaNode>> properties,
        Optional<Set<String>> required,
        @JsonProperty("enum") Optional<List<JsonNode>> enumeratedValues,
        Optional<ObjectSchemaNode> items,
        Optional<Boolean> additionalProperties) {

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

    public static class Builder {

        private Optional<String> type = Optional.empty();
        private Optional<Map<String, ObjectSchemaNode>> properties = Optional.empty();
        private Optional<Set<String>> required = Optional.empty();
        private Optional<List<JsonNode>> enumeratedValues = Optional.empty();
        private Optional<ObjectSchemaNode> items = Optional.empty();
        private Optional<Boolean> additionalProperties = Optional.empty();

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

        public Builder additionalProperties(boolean additionalProperties) {
            this.additionalProperties = Optional.of(additionalProperties);
            return this;
        }

        public ObjectSchemaNode build() {
            return new ObjectSchemaNode(type, properties, required, enumeratedValues, items, additionalProperties);
        }
    }
}
