package ca.ibodrov.mica.schema;

import java.util.Map;
import java.util.Optional;

public record ValidationResult(Map<String, ValidatedProperty> properties) {

    public Optional<ValidatedProperty> getProperty(String key) {
        return Optional.ofNullable(properties().get(key));
    }
}
