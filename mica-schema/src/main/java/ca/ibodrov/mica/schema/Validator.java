package ca.ibodrov.mica.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ca.ibodrov.mica.schema.StandardTypes.*;
import static ca.ibodrov.mica.schema.ValidationError.Kind.INVALID_SCHEMA;

public class Validator {

    private final ObjectMapper objectMapper;

    public Validator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ValidationResult validateMap(ObjectSchemaNode schema, Map<String, Object> input) {
        var validatedProperties = new HashMap<String, ValidatedProperty>();

        schema.properties().orElseGet(Map::of)
                .forEach((propertyName,
                          propertySchema) -> validateProperty(schema, propertyName, input.get(propertyName))
                                  .ifPresent(validatedProperty -> validatedProperties.put(propertyName,
                                          validatedProperty)));

        return new ValidationResult(validatedProperties);
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

        var type = property.type()
                .or(() -> getTypeFromConst(property))
                .orElse(OBJECT_TYPE);

        return switch (type) {
            case ANY_TYPE -> validateAny(property, input);
            case OBJECT_TYPE -> validateObject(property, input);
            case STRING_TYPE -> validateString(property, input);
            case NUMBER_TYPE -> validateNumber(property, input);
            // TODO other types
            default -> Optional.of(ValidatedProperty.unexpectedType(type));
        };
    }

    public Optional<ValidatedProperty> validateAny(ObjectSchemaNode property, Object input) {
        // "any" does not allow properties
        if (!property.properties().map(Map::isEmpty).orElse(true)) {
            return Optional.of(ValidatedProperty.invalid(new ValidationError(INVALID_SCHEMA,
                    Map.of("details", TextNode.valueOf("'any' does not allow 'properties'")))));
        }

        if (input == null) {
            return Optional.empty();
        }

        try {
            var actualValue = objectMapper.convertValue(input, JsonNode.class);

            return Optional.of(ValidatedProperty.valid(actualValue));
        } catch (IllegalArgumentException e) {
            return Optional.of(ValidatedProperty.invalidType("any", input.getClass()));
        }
    }

    public Optional<ValidatedProperty> validateObject(ObjectSchemaNode property, Object input) {
        var properties = property.properties().orElseGet(Map::of);
        var constant = getConstant(property);

        // no nested schema properties defined, nothing to validate
        // null is a valid value for an empty object
        if (constant.isEmpty() && (properties.isEmpty() || input == null)) {
            return Optional.empty();
        }

        // walk the nested schema properties and validate each one
        if (input instanceof Map<?, ?> m) {
            var result = constant.map(expectedObject -> {
                if (!expectedObject.isObject()) {
                    return ValidatedProperty.invalidType(OBJECT_TYPE, expectedObject.getClass());
                }

                var actualObject = objectMapper.convertValue(m, JsonNode.class);
                if (!expectedObject.equals(actualObject)) {
                    return ValidatedProperty.unexpectedValue(STRING_TYPE, expectedObject, actualObject);
                }

                return ValidatedProperty.valid(actualObject);
            });

            // fail: doesn't match the const value, early return
            if (result.isPresent() && !result.map(ValidatedProperty::isValid).orElse(false)) {
                return result;
            }

            // check the nested properties
            var nestedProperties = new HashMap<String, ValidatedProperty>();
            properties.forEach((key, propertySchema) -> validateProperty(propertySchema, key, m.get(key))
                    .ifPresent(p -> nestedProperties.put(key, p)));

            // no nested properties found, return the current result
            if (nestedProperties.isEmpty()) {
                return result;
            }

            return Optional.of(ValidatedProperty.nested(nestedProperties)
                    .withValue(result.flatMap(ValidatedProperty::value)));
        } else {
            return Optional.of(ValidatedProperty.invalidType("object", input.getClass()));
        }
    }

    public Optional<ValidatedProperty> validateString(ObjectSchemaNode property, Object input) {
        if (input == null) {
            return Optional.empty();
        }

        if (input instanceof String s) {
            var actualValue = TextNode.valueOf(s);
            return getConstant(property).map(expectedValue -> {
                if (!expectedValue.isTextual()) {
                    return ValidatedProperty.invalidType(STRING_TYPE, expectedValue.getClass());
                } else if (!expectedValue.equals(actualValue)) {
                    return ValidatedProperty.unexpectedValue(STRING_TYPE, expectedValue, actualValue);
                }
                return ValidatedProperty.valid(actualValue);
            }).or(() -> {
                return Optional.of(ValidatedProperty.valid(actualValue));
            });
        }

        return Optional.of(ValidatedProperty.invalidType("string", input.getClass()));
    }

    public Optional<ValidatedProperty> validateNumber(ObjectSchemaNode property, Object input) {
        if (input == null) {
            return Optional.empty();
        }

        if (input instanceof Number n) {
            var actualValue = objectMapper.convertValue(n, JsonNode.class);
            return getConstant(property).map(expectedValue -> {
                if (!expectedValue.isNumber()) {
                    return ValidatedProperty.invalidType(NUMBER_TYPE, expectedValue.getClass());
                } else if (!expectedValue.equals(actualValue)) {
                    return ValidatedProperty.unexpectedValue(NUMBER_TYPE, expectedValue, actualValue);
                }
                return ValidatedProperty.valid(actualValue);
            });
        }

        return Optional.of(ValidatedProperty.invalidType("number", input.getClass()));
    }

    private Optional<JsonNode> getConstant(ObjectSchemaNode property) {
        // deserializing a missing 'const' results in a non-empty Optional with NullNode
        // value -- we treat those as missing values
        return property.constant().filter(n -> !n.isNull());
    }

    private static Optional<String> getTypeFromConst(ObjectSchemaNode property) {
        return property.constant().map(v -> {
            if (v.isTextual()) {
                return "string";
            } else if (v.isNumber()) {
                return "number";
            } else if (v.isObject()) {
                return "object";
            } else {
                throw new RuntimeException("Unsupported 'const' node type: " + v.getNodeType());
            }
        });
    }
}
