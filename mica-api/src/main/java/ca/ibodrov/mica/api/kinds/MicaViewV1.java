package ca.ibodrov.mica.api.kinds;

import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.api.model.ViewLike;
import ca.ibodrov.mica.api.validation.ValidName;
import ca.ibodrov.mica.schema.ObjectSchemaNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.validation.constraints.NotNull;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * @implNote when adding or removing fields here, make sure to update
 *           {@link #toPartialEntity(ObjectMapper)}
 */
public record MicaViewV1(@ValidName String name,
        @NotNull Selector selector,
        @NotNull Data data,
        @NotNull Optional<Validation> validation,
        @NotNull Optional<ObjectSchemaNode> parameters) implements ViewLike {

    public static final String MICA_VIEW_V1 = "/mica/view/v1";

    public MicaViewV1 {
        requireNonNull(name, "missing 'name'");
        requireNonNull(selector, "missing 'selector'");
        requireNonNull(data, "missing 'data'");
        requireNonNull(validation, "'validation' cannot be null");
        requireNonNull(parameters, "'parameters' cannot be null");
    }

    public record Selector(Optional<List<String>> includes,
            @ValidName String entityKind,
            @NotNull Optional<List<String>> namePatterns) implements ViewLike.Selector {

        public static Selector byEntityKind(String entityKind) {
            return new Selector(Optional.empty(), entityKind, Optional.empty());
        }

        public Selector withNamePatterns(List<String> namePatterns) {
            return new Selector(Optional.empty(), this.entityKind, Optional.of(namePatterns));
        }
    }

    public record Data(String jsonPath,
            Optional<JsonNode> jsonPatch,
            Optional<Boolean> flatten,
            Optional<Boolean> merge) implements ViewLike.Data {

        public static Data jsonPath(String jsonPath) {
            return new Data(jsonPath, Optional.empty(), Optional.empty(), Optional.empty());
        }

        public Data withMerge() {
            return new Data(this.jsonPath, this.jsonPatch, this.flatten, Optional.of(true));
        }
    }

    public record Validation(String asEntityKind) implements ViewLike.Validation {

        public static Validation asEntityKind(String asEntityKind) {
            return new Validation(asEntityKind);
        }
    }

    public PartialEntity toPartialEntity(ObjectMapper objectMapper) {
        var props = new HashMap<String, JsonNode>();
        this.parameters.ifPresent(stringObjectSchemaNodeMap -> props.put("parameters",
                objectMapper.convertValue(stringObjectSchemaNodeMap, JsonNode.class)));
        props.put("selector", objectMapper.convertValue(this.selector, JsonNode.class));
        props.put("data", objectMapper.convertValue(this.data, JsonNode.class));
        props.put("validation", objectMapper.convertValue(this.validation, JsonNode.class));
        return PartialEntity.create(this.name, MICA_VIEW_V1, props);
    }

    public static class Builder {

        private String name;
        private Selector selector;
        private Data data;
        private Optional<Validation> validation = Optional.empty();
        private Optional<ObjectSchemaNode> parameters = Optional.empty();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder selector(Selector selector) {
            this.selector = selector;
            return this;
        }

        public Builder data(Data data) {
            this.data = data;
            return this;
        }

        public Builder validation(Validation validation) {
            this.validation = Optional.of(validation);
            return this;
        }

        public Builder parameters(ObjectSchemaNode parameters) {
            this.parameters = Optional.of(parameters);
            return this;
        }

        public MicaViewV1 build() {
            return new MicaViewV1(name, selector, data, validation, parameters);
        }
    }
}
