package ca.ibodrov.mica.schema;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ca.ibodrov.mica.schema.ValidationError.Kind.*;

public record ValidatedProperty(Optional<JsonNode> value,
        Optional<ValidationError> error,
        Optional<Map<String, ValidatedProperty>> properties) {

    public static ValidatedProperty valid(JsonNode value) {
        return new ValidatedProperty(Optional.of(value), Optional.empty(), Optional.empty());
    }

    public static ValidatedProperty nested(Map<String, ValidatedProperty> properties) {
        return new ValidatedProperty(Optional.empty(), Optional.empty(), Optional.of(properties));
    }

    public static ValidatedProperty invalid(ValidationError error) {
        return new ValidatedProperty(Optional.empty(), Optional.of(error), Optional.empty());
    }

    public static ValidatedProperty unexpectedType(ValueType unexpectedType) {
        return invalid(
                new ValidationError(INVALID_SCHEMA, Map.of("unexpectedType", TextNode.valueOf(unexpectedType.key()))));
    }

    public static ValidatedProperty unexpectedValue(ValueType expectedType,
                                                    JsonNode expectedValue,
                                                    JsonNode actualValue) {
        return invalid(new ValidationError(UNEXPECTED_VALUE,
                Map.of("expectedType", TextNode.valueOf(expectedType.key()),
                        "expectedValue", expectedValue,
                        "actualValue", actualValue)));
    }

    public static ValidatedProperty unexpectedProperties(String details, Set<String> propertyNames) {
        return invalid(new ValidationError(UNEXPECTED_VALUE,
                Map.of("details", TextNode.valueOf(details),
                        "propertyNames", new ArrayNode(null, propertyNames.stream()
                                .map(TextNode::valueOf)
                                .map(n -> (JsonNode) n)
                                .toList()))));
    }

    public static ValidatedProperty invalidType(ValueType expectedType, JsonNode actualValue) {
        return invalid(new ValidationError(INVALID_TYPE,
                Map.of("expected", TextNode.valueOf(expectedType.key()),
                        "actualValue", actualValue)));
    }

    public static ValidatedProperty missingRequiredProperty(String propertyName) {
        return invalid(new ValidationError(MISSING_PROPERTY,
                Map.of("propertyName", TextNode.valueOf(propertyName))));
    }

    public static ValidatedProperty invalidSchema(String details) {
        return invalid(new ValidationError(INVALID_SCHEMA,
                Map.of("details", TextNode.valueOf(details))));
    }

    @JsonIgnore
    public boolean isValid() {
        // TODO non-recursive version

        if (error().isPresent()) {
            return false;
        }

        return properties().map(props -> {
            for (var prop : props.values()) {
                if (!prop.isValid()) {
                    return false;
                }
            }
            return true;
        }).orElse(true);
    }

    @JsonIgnore
    public ValidatedProperty withValue(Optional<JsonNode> value) {
        return new ValidatedProperty(value, this.error, this.properties);
    }
}
