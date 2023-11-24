package ca.ibodrov.mica.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static ca.ibodrov.mica.schema.StandardTypes.*;
import static ca.ibodrov.mica.schema.ValidationError.Kind.INVALID_SCHEMA;

public class Validator {

    private static ValidatedProperty validateProperty(String name,
                                                      ObjectSchemaNode property,
                                                      boolean required,
                                                      JsonNode input) {

        // TODO non-recursive depth-first traversal of the schema (using a command
        // queue)

        var type = property.type()
                .or(() -> getTypeFromConst(property))
                .orElse(OBJECT_TYPE);

        var validatedProperty = switch (type) {
            case ANY_TYPE -> validateAny(property, input);
            case OBJECT_TYPE -> validateObject(property, input);
            case STRING_TYPE -> validateString(property, input);
            case NUMBER_TYPE -> validateNumber(property, input);
            case NULL_TYPE -> validateNull(property, input);
            // TODO other types
            default -> ValidatedProperty.unexpectedType(type);
        };

        if (required && !validatedProperty.isValid()) {
            return ValidatedProperty.missingRequiredProperty(TextNode.valueOf(name));
        }

        return validatedProperty;
    }

    public static ValidatedProperty validateAny(ObjectSchemaNode property, JsonNode input) {
        // "any" does not allow properties
        if (!property.properties().map(Map::isEmpty).orElse(true)) {
            return ValidatedProperty.invalid(new ValidationError(INVALID_SCHEMA,
                    Map.of("details", TextNode.valueOf("'any' does not allow 'properties'"))));
        }

        try {
            return ValidatedProperty.valid(input);
        } catch (IllegalArgumentException e) {
            return ValidatedProperty.invalidType("any", input);
        }
    }

    public static ValidatedProperty validateObject(ObjectSchemaNode property, JsonNode input) {
        if (!input.isObject()) {
            return ValidatedProperty.invalidType("object", input);
        }

        // validate the constant value
        var constant = getConstant(property);
        if (constant.isPresent()) {
            var expectedValue = constant.get();
            if (!expectedValue.isObject()) {
                return ValidatedProperty.invalidType(OBJECT_TYPE, expectedValue);
            }

            if (!expectedValue.equals(input)) {
                return ValidatedProperty.unexpectedValue(STRING_TYPE, expectedValue, input);
            }

            return ValidatedProperty.valid(input);
        }

        // check the nested properties
        var validatedProperties = new HashMap<String, ValidatedProperty>();
        var properties = property.properties().orElseGet(Map::of);
        properties.forEach((key, prop) -> {
            var required = property.required().map(props -> props.contains(key)).orElse(false);
            var value = Optional.ofNullable(input.get(key)).orElse(NullNode.getInstance());
            validatedProperties.put(key, validateProperty(key, prop, required, value));
        });

        // no nested properties found, return the current result
        if (validatedProperties.isEmpty()) {
            return ValidatedProperty.valid(input);
        }

        return ValidatedProperty.nested(validatedProperties).withValue(Optional.of(input));
    }

    public static ValidatedProperty validateString(ObjectSchemaNode property, JsonNode input) {
        if (!input.isTextual()) {
            return ValidatedProperty.invalidType(STRING_TYPE, input);
        }

        return getConstant(property).map(expectedValue -> {
            if (!expectedValue.isTextual()) {
                return ValidatedProperty.invalidType(STRING_TYPE, expectedValue);
            } else if (!expectedValue.equals(input)) {
                return ValidatedProperty.unexpectedValue(STRING_TYPE, expectedValue, input);
            }
            return ValidatedProperty.valid(input);
        }).orElseGet(() -> ValidatedProperty.valid(input));
    }

    public static ValidatedProperty validateNumber(ObjectSchemaNode property, JsonNode input) {
        if (!input.isNumber()) {
            return ValidatedProperty.invalidType("number", input);
        }

        return getConstant(property).map(expectedValue -> {
            if (!expectedValue.isNumber()) {
                return ValidatedProperty.invalidType(NUMBER_TYPE, expectedValue);
            } else if (!expectedValue.equals(input)) {
                return ValidatedProperty.unexpectedValue(NUMBER_TYPE, expectedValue, input);
            }
            return ValidatedProperty.valid(input);
        }).orElseGet(() -> ValidatedProperty.valid(input));
    }

    public static ValidatedProperty validateNull(ObjectSchemaNode property, JsonNode input) {
        if (!input.isNull()) {
            return ValidatedProperty.unexpectedValue(NULL_TYPE, NullNode.getInstance(), input);
        }

        var expectedValue = property.constant().orElse(NullNode.getInstance());
        if (!expectedValue.isNull()) {
            return ValidatedProperty.invalid(new ValidationError(INVALID_SCHEMA,
                    Map.of("details",
                            TextNode.valueOf("Type 'null' disallows any 'const' values other than null."))));
        }

        return ValidatedProperty.valid(NullNode.getInstance());
    }

    private static Optional<JsonNode> getConstant(ObjectSchemaNode property) {
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
