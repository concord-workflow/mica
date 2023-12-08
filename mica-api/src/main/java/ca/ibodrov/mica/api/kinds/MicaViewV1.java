package ca.ibodrov.mica.api.kinds;

import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.api.model.ViewLike;
import ca.ibodrov.mica.api.validation.ValidName;
import ca.ibodrov.mica.schema.ObjectSchemaNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.validation.constraints.NotNull;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record MicaViewV1(@ValidName String name,
        @NotNull Selector selector,
        @NotNull Data data,
        @NotNull Optional<Map<String, ObjectSchemaNode>> parameters) implements ViewLike {

    public static final String MICA_VIEW_V1 = "/mica/view/v1";

    public MicaViewV1 {
        requireNonNull(name);
        requireNonNull(selector);
        requireNonNull(data);
        requireNonNull(parameters);
    }

    public record Selector(@ValidName String entityKind) implements ViewLike.Selector {

        public static Selector byEntityKind(String entityKind) {
            return new Selector(entityKind);
        }
    }

    public record Data(String jsonPath,
            Optional<JsonNode> jsonPatch,
            Optional<Boolean> flatten,
            Optional<Boolean> merge) implements ViewLike.Data {

        public static Data jsonPath(String jsonPath) {
            return new Data(jsonPath, Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    public PartialEntity asPartialEntity(ObjectMapper objectMapper) {
        return PartialEntity.create(this.name, MICA_VIEW_V1,
                Map.of("parameters", objectMapper.convertValue(this.parameters, JsonNode.class),
                        "selector", objectMapper.convertValue(this.selector, JsonNode.class),
                        "data", objectMapper.convertValue(this.data, JsonNode.class)));
    }

    public static class Builder {

        private String name;
        private Selector selector;
        private Data data;
        private Optional<Map<String, ObjectSchemaNode>> parameters = Optional.empty();

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

        public Builder parameters(Map<String, ObjectSchemaNode> parameters) {
            this.parameters = Optional.of(parameters);
            return this;
        }

        public MicaViewV1 build() {
            return new MicaViewV1(name, selector, data, parameters);
        }
    }
}
