package ca.ibodrov.mica.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.*;
import java.util.function.Function;

import static ca.ibodrov.mica.schema.ValidatedProperty.*;
import static ca.ibodrov.mica.schema.ValidationError.Kind.INVALID_SCHEMA;
import static ca.ibodrov.mica.schema.ValueType.*;

/**
 * A simple recursive validator. Supports a subset of JSON schema features:
 * <ul>
 * <li>types: object, array, string, number, boolean, null</li>
 * <li>properties</li>
 * <li><i>required<i>properties</li>
 * <li><i>additionalProperties</i></li>
 * <li><i>enum</i> values</li>
 * <li>array <i>items</i> (only item schema validation, no <i>prefixItems</i>
 * with <i>items: false</i> support yet)</li>
 * <li><i>$ref</i></li>
 * </ul>
 */
public class Validator {

    private final Function<String, Optional<ObjectSchemaNode>> schemaResolver;

    public Validator() {
        schemaResolver = (id) -> Optional.empty();
    }

    public Validator(Function<String, Optional<ObjectSchemaNode>> schemaResolver) {
        this.schemaResolver = schemaResolver;
    }

    /**
     * Validates input against the given schema. Returns a {@link ValidatedProperty}
     * instance that contains all schema fields and their validation results.
     * Additional properties are returned only if their types or values are
     * validated.
     *
     * @param property the schema
     * @param input    input data
     * @return validated data.
     */
    public ValidatedProperty validateObject(ObjectSchemaNode property, JsonNode input) {
        if (!input.isObject()) {
            return invalidType(OBJECT, input);
        }

        // validate the enum value
        var enums = property.enumeratedValues();
        if (enums.isPresent()) {
            return validateEnums(enums.get(), OBJECT, input);
        }

        // track any unknown properties in the input object
        var unknownInputKeys = new HashSet<String>(input.size());
        input.fieldNames().forEachRemaining(unknownInputKeys::add);

        // check "properties"
        var validatedProperties = new HashMap<String, ValidatedProperty>();
        var properties = property.properties().orElseGet(Map::of);
        properties.forEach((key, prop) -> {
            unknownInputKeys.remove(key);

            var required = property.required().map(props -> props.contains(key)).orElse(false);
            var value = Optional.ofNullable(input.get(key)).orElse(NullNode.getInstance());
            var validatedProp = validateProperty(key, prop, required, value);
            validatedProperties.put(key, validatedProp);
        });

        // check "additionalProperties"
        // https://json-schema.org/understanding-json-schema/reference/object#additionalproperties
        if (property.additionalProperties().filter(v -> !v.isNull()).isPresent()) {
            var additionalProperties = property.additionalProperties().get();

            // additionalProperties can be a boolean or an object
            if (additionalProperties.isBoolean()) {
                if (!additionalProperties.booleanValue() && !unknownInputKeys.isEmpty()) {
                    return unexpectedProperties("Additional properties are not allowed: " + unknownInputKeys,
                            unknownInputKeys);
                }
            } else if (additionalProperties.isObject()) {
                var schema = ObjectSchemaNode.fromObjectNode((ObjectNode) additionalProperties);
                var validatedAdditionalProperties = new HashMap<String, ValidatedProperty>();
                unknownInputKeys.forEach(key -> {
                    var value = Optional.ofNullable(input.get(key)).orElse(NullNode.getInstance());
                    var validatedProp = validateProperty(key, schema, false, value);
                    validatedAdditionalProperties.put(key, validatedProp);
                });
                validatedProperties.putAll(validatedAdditionalProperties);
            } else {
                return invalidSchema("'additionalProperties' must be a boolean or a schema object");
            }
        }

        // no nested properties found, return the current result
        if (validatedProperties.isEmpty()) {
            return valid(input);
        }

        return ValidatedProperty.nested(validatedProperties).withValue(Optional.of(input));
    }

    private ValidatedProperty validateProperty(String name,
                                               ObjectSchemaNode property,
                                               boolean required,
                                               JsonNode input) {

        // follow the $ref until all references are resolved
        while (true) {
            var ref = property.ref();
            if (ref.isEmpty()) {
                break;
            }

            var schema = ref.flatMap(schemaResolver);
            if (schema.isEmpty()) {
                return invalidSchema("Schema not found, $ref: " + ref);
            }

            property = schema.get();
        }

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
                return invalidSchema("'enum' values must be of the same type");
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
        };
    }

    private ValidatedProperty validateArray(ObjectSchemaNode property, JsonNode input) {
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

    private static ValidatedProperty validateAny(ObjectSchemaNode property, JsonNode input) {
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

    private static ValidatedProperty validateBoolean(ObjectSchemaNode property, JsonNode input) {
        if (!input.isBoolean()) {
            return invalidType(BOOLEAN, input);
        }

        return property.enumeratedValues()
                .map(enums -> validateEnums(enums, BOOLEAN, input))
                .orElseGet(() -> valid(input));
    }

    private static ValidatedProperty validateString(ObjectSchemaNode property, JsonNode input) {
        if (!input.isTextual()) {
            return invalidType(STRING, input);
        }

        return property.enumeratedValues()
                .map(enums -> validateEnums(enums, STRING, input))
                .orElseGet(() -> valid(input));
    }

    private static ValidatedProperty validateNumber(ObjectSchemaNode property, JsonNode input) {
        if (!input.isNumber()) {
            return invalidType(NUMBER, input);
        }

        return property.enumeratedValues()
                .map(enums -> validateEnums(enums, NUMBER, input))
                .orElseGet(() -> valid(input));
    }

    private static ValidatedProperty validateNull(ObjectSchemaNode property, JsonNode input) {
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
