package ca.ibodrov.mica.schema;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Map;
import java.util.Optional;

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

    public static ValidatedProperty unexpectedType(String unexpectedType) {
        return invalid(new ValidationError(INVALID_SCHEMA, Map.of("unexpectedType", TextNode.valueOf(unexpectedType))));
    }

    public static ValidatedProperty unexpectedValue(String expectedType, JsonNode expectedValue, JsonNode actualValue) {
        return invalid(new ValidationError(UNEXPECTED_VALUE, Map.of("expectedType", TextNode.valueOf(expectedType),
                "expectedValue", expectedValue,
                "actualValue", actualValue)));
    }

    public static ValidatedProperty invalidType(String expectedType, JsonNode actualValue) {
        return invalid(new ValidationError(INVALID_TYPE,
                Map.of("expected", TextNode.valueOf(expectedType),
                        "actualValue", actualValue)));
    }

    public static ValidatedProperty missingRequiredProperty(JsonNode propertyName) {
        return invalid(new ValidationError(MISSING_PROPERTY, Map.of("propertyName", propertyName)));
    }

    public static ValidatedProperty invalidSchema(String details) {
        return invalid(new ValidationError(INVALID_SCHEMA, Map.of("details", TextNode.valueOf(details))));
    }

    @JsonIgnore
    public boolean isValid() {
        return error().isEmpty();
    }

    @JsonIgnore
    public ValidatedProperty withValue(Optional<JsonNode> value) {
        return new ValidatedProperty(value, this.error, this.properties);
    }
}
