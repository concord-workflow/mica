package ca.ibodrov.mica.schema;

import com.fasterxml.jackson.databind.JsonNode;

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
        return invalid(new ValidationError(INVALID_SCHEMA, Map.of("unexpectedType", unexpectedType)));
    }

    public static ValidatedProperty invalidType(String expectedType, Class<?> actualJavaClass) {
        return invalid(new ValidationError(INVALID_TYPE,
                Map.of("expected", expectedType, "actualJavaClass", actualJavaClass.getName())));
    }

    public static ValidatedProperty missingProperty(String propertyName) {
        return invalid(new ValidationError(MISSING_PROPERTY, Map.of("propertyName", propertyName)));
    }
}
