package ca.ibodrov.mica.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.HashMap;
import java.util.List;
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

        Optional<String> firstEnumValueType = Optional.empty();
        var enums = property.enumeratedValues();
        if (enums.isPresent()) {
            var enumValues = enums.get();
            if (enumValues.isEmpty()) {
                return ValidatedProperty.invalidSchema("'enum' must not be empty");
            }
            // check if all "enum" values are of the same type
            var firstNodeType = typeOf(enumValues.get(0));
            if (enumValues.stream().map(Validator::typeOf).anyMatch(t -> !t.equals(firstNodeType))) {
                throw new IllegalArgumentException("'enum' values must be of the same type");
            }
            firstEnumValueType = Optional.of(firstNodeType);
        }

        var typeFromEnum = firstEnumValueType;
        var type = property.type()
                .or(() -> typeFromEnum)
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

        // validate the enum value
        var enums = property.enumeratedValues();
        if (enums.isPresent()) {
            return validateEnums(enums.get(), OBJECT_TYPE, input);
        }

        // check the nested properties
        var validatedProperties = new HashMap<String, ValidatedProperty>();
        var properties = property.properties().orElseGet(Map::of);
        properties.forEach((key, prop) -> {
            var required = property.required().map(props -> props.contains(key)).orElse(false);
            var value = Optional.ofNullable(input.get(key)).orElse(NullNode.getInstance());
            var validatedProp = validateProperty(key, prop, required, value);
            validatedProperties.put(key, validatedProp);
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

        return property.enumeratedValues()
                .map(enums -> validateEnums(enums, STRING_TYPE, input))
                .orElseGet(() -> ValidatedProperty.valid(input));
    }

    public static ValidatedProperty validateNumber(ObjectSchemaNode property, JsonNode input) {
        if (!input.isNumber()) {
            return ValidatedProperty.invalidType("number", input);
        }

        return property.enumeratedValues()
                .map(enums -> validateEnums(enums, NUMBER_TYPE, input))
                .orElseGet(() -> ValidatedProperty.valid(input));
    }

    public static ValidatedProperty validateNull(ObjectSchemaNode property, JsonNode input) {
        if (!input.isNull()) {
            return ValidatedProperty.unexpectedValue(NULL_TYPE, NullNode.getInstance(), input);
        }

        var expectedValues = property.enumeratedValues().orElseGet(List::of);
        if (!expectedValues.isEmpty()) {
            return ValidatedProperty.invalid(new ValidationError(INVALID_SCHEMA,
                    Map.of("details",
                            TextNode.valueOf("Type 'null' does not allow 'enum' values."))));
        }

        return ValidatedProperty.valid(NullNode.getInstance());
    }

    private static ValidatedProperty validateEnums(List<JsonNode> enums, String expectedType, JsonNode input) {
        assert !enums.isEmpty();
        var firstExpectedValue = enums.get(0);
        if (!expectedType.equals(typeOf(firstExpectedValue))) {
            return ValidatedProperty.invalidType(OBJECT_TYPE, firstExpectedValue);
        }

        // TODO in case of error report all 'enum' values, not just the first one
        return enums.stream().filter(expectedValue -> expectedValue.equals(input))
                .findFirst()
                .map(ValidatedProperty::valid)
                .orElseGet(() -> ValidatedProperty.unexpectedValue(expectedType, firstExpectedValue, input));
    }

    private static String typeOf(JsonNode v) {
        if (v.isTextual()) {
            return "string";
        } else if (v.isNumber()) {
            return "number";
        } else if (v.isObject()) {
            return "object";
        } else {
            throw new RuntimeException("Unsupported 'enum' node type: " + v.getNodeType());
        }
    }
}
