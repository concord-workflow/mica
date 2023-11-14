package ca.ibodrov.mica.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ca.ibodrov.mica.schema.StandardTypes.*;

public class Validator {

    private final ObjectMapper objectMapper;

    public Validator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, ValidatedProperty> validateMap(ObjectSchemaNode schema, Map<String, Object> input) {
        var result = new HashMap<String, ValidatedProperty>();

        schema.properties().orElseGet(Map::of)
                .forEach((propertyName,
                          propertySchema) -> validateProperty(schema, propertyName, input.get(propertyName))
                                  .ifPresent(validatedProperty -> result.put(propertyName, validatedProperty)));

        return result;
    }

    public Optional<ValidatedProperty> validateProperty(ObjectSchemaNode parentSchema,
                                                        String propertyName,
                                                        Object input) {

        // TODO non-recursive depth-first traversal of the schema (using a command
        // queue)

        var property = parentSchema.getProperty(propertyName)
                .orElseThrow(() -> new RuntimeException("Missing property '%s' in schema".formatted(propertyName)));

        if (input == null) {
            var isRequired = parentSchema.required().orElseGet(Set::of).contains(propertyName);
            if (isRequired) {
                return Optional.of(ValidatedProperty.missingProperty(propertyName));
            }
            return Optional.empty();
        }

        var type = property.type().orElse(OBJECT_TYPE);
        return switch (type) {
            case OBJECT_TYPE -> validateObject(property, input);
            case STRING_TYPE -> validateString(property, input);
            case NUMBER_TYPE -> validateNumber(property, input);
            // TODO other types
            default -> Optional.of(ValidatedProperty.unexpectedType(type));
        };
    }

    public Optional<ValidatedProperty> validateObject(ObjectSchemaNode property, Object input) {
        assert property.type().equals(Optional.of("object"));

        var properties = property.properties().orElseGet(Map::of);

        // no nested schema properties defined, nothing to validate
        // null is a valid value for an empty object
        if (properties.isEmpty() || input == null) {
            return Optional.empty();
        }

        // walk the nested schema properties and validate each one
        if (input instanceof Map<?, ?> m) {
            var nestedProperties = new HashMap<String, ValidatedProperty>();
            properties.forEach(
                    (key, propertySchema) -> validateProperty(propertySchema, key, m.get(key))
                            .ifPresent(p -> nestedProperties.put(key, p)));

            if (nestedProperties.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(ValidatedProperty.nested(nestedProperties));
        } else {
            return Optional.of(ValidatedProperty.invalidType("object", input.getClass()));
        }
    }

    public Optional<ValidatedProperty> validateString(ObjectSchemaNode property, Object input) {
        assert property.type().equals(Optional.of("string"));

        if (input == null) {
            return Optional.empty();
        }

        if (input instanceof String s) {
            return Optional.of(ValidatedProperty.valid(TextNode.valueOf(s)));
        }

        return Optional.of(ValidatedProperty.invalidType("string", input.getClass()));
    }

    public Optional<ValidatedProperty> validateNumber(ObjectSchemaNode property, Object input) {
        assert property.type().equals(Optional.of("number"));

        if (input == null) {
            return Optional.empty();
        }

        if (input instanceof Number n) {
            // TODO convert value manually to avoid dependency on jackson
            return Optional.of(ValidatedProperty.valid(objectMapper.convertValue(n, JsonNode.class)));
        }

        return Optional.of(ValidatedProperty.invalidType("number", input.getClass()));
    }
}
