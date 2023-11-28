package ca.ibodrov.mica.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ca.ibodrov.mica.schema.ValidatedProperty.*;
import static ca.ibodrov.mica.schema.ValidationError.Kind.INVALID_SCHEMA;
import static ca.ibodrov.mica.schema.ValueType.*;

// TODO non-recursive depth-first traversal of the schema
//  (using a command queue)
public class Validator {

    private static ValidatedProperty validateProperty(String name,
                                                      ObjectSchemaNode property,
                                                      boolean required,
                                                      JsonNode input) {

        // looks ugly
        Optional<ValueType> firstEnumValueType = Optional.empty();
        var enums = property.enumeratedValues();
        if (enums.isPresent()) {
            var enumValues = enums.get();
            if (enumValues.isEmpty()) {
                return invalidSchema("'enum' must not be empty");
            }
            // check if all "enum" values are of the same type
            var firstNodeType = ValueType.typeOf(enumValues.get(0));
            if (enumValues.stream().map(ValueType::typeOf).anyMatch(t -> !t.equals(firstNodeType))) {
                throw new IllegalArgumentException("'enum' values must be of the same type");
            }
            firstEnumValueType = Optional.of(firstNodeType);
        }

        if (required && input.isNull()) {
            return missingRequiredProperty(name);
        }

        if (!required && input.isNull()) {
            return valid(input);
        }

        var typeFromEnum = firstEnumValueType;
        var type = property.type()
                .map(ValueType::ofKey)
                .or(() -> typeFromEnum)
                .orElse(OBJECT);

        return switch (type) {
            case ANY -> validateAny(property, input);
            case ARRAY -> validateArray(property, input);
            case BOOLEAN -> validateBoolean(property, input);
            case NUMBER -> validateNumber(property, input);
            case OBJECT -> validateObject(property, input);
            case STRING -> validateString(property, input);
            case NULL -> validateNull(property, input);
            // TODO other types
            default -> unexpectedType(type);
        };
    }

    public static ValidatedProperty validateAny(ObjectSchemaNode property, JsonNode input) {
        // "any" does not allow properties
        if (!property.properties().map(Map::isEmpty).orElse(true)) {
            return invalid(new ValidationError(INVALID_SCHEMA,
                    Map.of("details", TextNode.valueOf("'any' does not allow 'properties'"))));
        }

        try {
            return valid(input);
        } catch (IllegalArgumentException e) {
            return invalidType(ValueType.ANY, input);
        }
    }

    public static ValidatedProperty validateArray(ObjectSchemaNode property, JsonNode input) {
        if (!input.isArray()) {
            return invalidType(ARRAY, input);
        }

        // validate the enum value
        var enums = property.enumeratedValues();
        if (enums.isPresent()) {
            return validateEnums(enums.get(), ARRAY, input);
        }

        // check the array items
        var maybeItems = property.items();
        if (maybeItems.isEmpty()) {
            return invalidSchema("'items' must be specified for 'array' type");
        }

        var validatedItems = new HashMap<String, ValidatedProperty>();
        var itemSchema = maybeItems.get();
        for (int i = 0; i < input.size(); i++) {
            var propertyKey = String.valueOf(i);
            var validatedItem = validateProperty(propertyKey, itemSchema, false, input.get(i));
            validatedItems.put(propertyKey, validatedItem);
        }

        // no array items found, return the current result
        if (validatedItems.isEmpty()) {
            return valid(input);
        }

        return ValidatedProperty.nested(validatedItems).withValue(Optional.of(input));
    }

    public static ValidatedProperty validateObject(ObjectSchemaNode property, JsonNode input) {
        if (!input.isObject()) {
            return invalidType(OBJECT, input);
        }

        // validate the enum value
        var enums = property.enumeratedValues();
        if (enums.isPresent()) {
            return validateEnums(enums.get(), OBJECT, input);
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
            return valid(input);
        }

        return ValidatedProperty.nested(validatedProperties).withValue(Optional.of(input));
    }

    public static ValidatedProperty validateBoolean(ObjectSchemaNode property, JsonNode input) {
        if (!input.isBoolean()) {
            return invalidType(BOOLEAN, input);
        }

        return property.enumeratedValues()
                .map(enums -> validateEnums(enums, BOOLEAN, input))
                .orElseGet(() -> valid(input));
    }

    public static ValidatedProperty validateString(ObjectSchemaNode property, JsonNode input) {
        if (!input.isTextual()) {
            return invalidType(STRING, input);
        }

        return property.enumeratedValues()
                .map(enums -> validateEnums(enums, STRING, input))
                .orElseGet(() -> valid(input));
    }

    public static ValidatedProperty validateNumber(ObjectSchemaNode property, JsonNode input) {
        if (!input.isNumber()) {
            return invalidType(NUMBER, input);
        }

        return property.enumeratedValues()
                .map(enums -> validateEnums(enums, NUMBER, input))
                .orElseGet(() -> valid(input));
    }

    public static ValidatedProperty validateNull(ObjectSchemaNode property, JsonNode input) {
        if (!input.isNull()) {
            return ValidatedProperty.unexpectedValue(NULL, NullNode.getInstance(), input);
        }

        var expectedValues = property.enumeratedValues().orElseGet(List::of);
        if (!expectedValues.isEmpty()) {
            return invalid(new ValidationError(INVALID_SCHEMA,
                    Map.of("details",
                            TextNode.valueOf("Type 'null' does not allow 'enum' values."))));
        }

        return valid(NullNode.getInstance());
    }

    private static ValidatedProperty validateEnums(List<JsonNode> enums, ValueType expectedType, JsonNode input) {
        assert !enums.isEmpty();
        var firstExpectedValue = enums.get(0);
        if (!expectedType.equals(typeOf(firstExpectedValue))) {
            return invalidType(expectedType, firstExpectedValue);
        }

        // TODO in case of error report all 'enum' values, not just the first one
        return enums.stream().filter(expectedValue -> expectedValue.equals(input))
                .findFirst()
                .map(ValidatedProperty::valid)
                .orElseGet(() -> ValidatedProperty.unexpectedValue(expectedType, firstExpectedValue, input));
    }
}
