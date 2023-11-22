package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.Profile;
import ca.ibodrov.mica.schema.Input;
import ca.ibodrov.mica.schema.ValidatedProperty;
import ca.ibodrov.mica.schema.Validator;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.inject.Inject;
import java.util.Map;
import java.util.stream.Collectors;

public class ProfileRenderer {

    private final ObjectMapper objectMapper;

    @Inject
    public ProfileRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EffectiveProfile render(Profile profile, Input input) {
        // the effective schema
        // TODO somewhere here we should merge schemas from all referenced profiles into
        var effectiveSchema = profile.schema();

        // we take all properties defined in the effective schema
        var knownSchemaProperties = effectiveSchema.properties().orElseGet(Map::of);

        // and grab input values for each known property, skipping missing values
        var values = knownSchemaProperties.keySet().stream()
                .filter(key -> input.properties().containsKey(key))
                .collect(Collectors.toMap(k -> k, k -> input.properties().get(k)));

        // create the effective properties
        var validator = new Validator(objectMapper);
        var validationResult = validator.validateMap(effectiveSchema, values);

        return new EffectiveProfile(profile.name(), validationResult.properties());
    }

    public record EffectiveProfile(String profileName, Map<String, ValidatedProperty> properties) {
    }
}
