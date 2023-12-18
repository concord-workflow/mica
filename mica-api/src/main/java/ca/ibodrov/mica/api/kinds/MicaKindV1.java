package ca.ibodrov.mica.api.kinds;

import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.api.validation.ValidName;
import ca.ibodrov.mica.schema.ObjectSchemaNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.validation.constraints.NotNull;
import java.util.HashMap;

import static java.util.Objects.requireNonNull;

public record MicaKindV1(@ValidName String name,
        @NotNull ObjectSchemaNode schema) {

    public static final String MICA_KIND_V1 = "/mica/kind/v1";
    public static final String SCHEMA_PROPERTY = "schema";

    public MicaKindV1 {
        requireNonNull(name, "missing 'name'");
        requireNonNull(schema, "missing 'schema'");
    }

    public PartialEntity toPartialEntity(ObjectMapper objectMapper) {
        var props = new HashMap<String, JsonNode>();
        props.put(SCHEMA_PROPERTY, objectMapper.convertValue(schema, JsonNode.class));
        return PartialEntity.create(this.name, MICA_KIND_V1, props);
    }

    public static class Builder {

        private String name;
        private ObjectSchemaNode schema;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder schema(ObjectSchemaNode schema) {
            this.schema = schema;
            return this;
        }

        public MicaKindV1 build() {
            return new MicaKindV1(name, schema);
        }
    }
}
