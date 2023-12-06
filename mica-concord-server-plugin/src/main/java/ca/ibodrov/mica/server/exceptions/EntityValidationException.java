package ca.ibodrov.mica.server.exceptions;

import ca.ibodrov.mica.schema.ValidatedProperty;
import ca.ibodrov.mica.schema.ValidationError;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class EntityValidationException extends RuntimeException {

    public static EntityValidationException from(ValidatedProperty validatedProperty) {
        var result = ImmutableMap.<String, ValidationError>builder();
        addErrors(result, "", validatedProperty);
        return new EntityValidationException(result.build());
    }

    private final Map<String, ValidationError> errors;

    public EntityValidationException(Map<String, ValidationError> errors) {
        this.errors = errors;
    }

    public Map<String, ValidationError> getErrors() {
        return errors;
    }

    private static void addErrors(ImmutableMap.Builder<String, ValidationError> result,
                                  String path,
                                  ValidatedProperty invalidProperty) {
        if (invalidProperty.isValid()) {
            return;
        }

        invalidProperty.error().ifPresent(error -> {
            result.put(path, error);
        });

        invalidProperty.properties().ifPresent(props -> {
            for (var entry : props.entrySet()) {
                addErrors(result, path + "." + entry.getKey(), entry.getValue());
            }
        });
    }
}
