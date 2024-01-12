package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.uri.URIFactory;
import com.networknt.schema.uri.URIFetcher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class Validator {

    private static final String[] ALL_MICA_SCHEMES = { "http", "https", "mica" };

    public static Validator getDefault(ObjectMapper objectMapper, SchemaFetcher schemaFetcher) {
        return new Validator(JsonSchemaFactory.builder()
                .objectMapper(objectMapper)
                .defaultMetaSchemaURI(JsonMetaSchema.getV202012().getUri())
                .addMetaSchema(JsonMetaSchema.getV202012())
                .uriFactory(new InternalURIFactory(), ALL_MICA_SCHEMES)
                .uriFetcher(new InternalURIFetcher(schemaFetcher), ALL_MICA_SCHEMES)
                .build());
    }

    private final JsonSchemaFactory jsonSchemaFactory;

    public Validator(JsonSchemaFactory jsonSchemaFactory) {
        this.jsonSchemaFactory = requireNonNull(jsonSchemaFactory);
    }

    public ValidatedInput validateObject(JsonNode schema, JsonNode input) {
        JsonSchema jsonSchema;
        try {
            jsonSchema = jsonSchemaFactory.getSchema(schema);
        } catch (JsonSchemaException e) {
            throw ApiException.badRequest("Invalid schema: " + e.getMessage());
        }
        var messages = jsonSchema.validate(input);
        return new ValidatedInput(messages);
    }

    private static class InternalURIFactory implements URIFactory {

        @Override
        public URI create(String uri) {
            return URI.create(uri);
        }

        @Override
        public URI create(URI baseURI, String segment) {
            return baseURI.resolve(segment);
        }
    }

    public interface SchemaFetcher {

        /**
         * Returns a JSON schema object for the specified kind.
         */
        Optional<InputStream> fetch(String kind) throws IOException;
    }

    public static class NoopSchemaFetcher implements SchemaFetcher {

        @Override
        public Optional<InputStream> fetch(String kind) {
            return Optional.empty();
        }
    }

    public static class BuiltinSchemasFetcher implements SchemaFetcher {

        private final BuiltinSchemas builtinSchemas;
        private final ObjectMapper objectMapper;

        public BuiltinSchemasFetcher(BuiltinSchemas builtinSchemas, ObjectMapper objectMapper) {
            this.builtinSchemas = requireNonNull(builtinSchemas);
            this.objectMapper = requireNonNull(objectMapper);
        }

        @Override
        public Optional<InputStream> fetch(String kind) {
            return builtinSchemas.get(kind).map(v -> {
                try {
                    return objectMapper.writeValueAsBytes(v);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }).map(ByteArrayInputStream::new);
        }
    }

    private static class InternalURIFetcher implements URIFetcher {

        private final SchemaFetcher schemaFetcher;

        private InternalURIFetcher(SchemaFetcher schemaFetcher) {
            this.schemaFetcher = requireNonNull(schemaFetcher);
        }

        @Override
        public InputStream fetch(URI uri) throws IOException {
            // TODO handle http/https
            // TODO consider rewriting URIs in URIFactory
            if (uri.getHost().equals("json-schema.org") && uri.getPath().startsWith("/draft/2020-12")) {
                var in = Validator.class.getResourceAsStream(uri.getPath());
                if (in == null) {
                    throw new IllegalArgumentException("Resource not found: " + uri.getPath());
                }
                return in;
            }

            if (!"mica".equals(uri.getScheme())) {
                throw new IllegalArgumentException("Unsupported scheme: " + uri.getScheme());
            }

            if (!"internal".equals(uri.getHost())) {
                throw new IllegalArgumentException("Unsupported host: " + uri.getHost());
            }

            String kind = uri.getPath();
            return schemaFetcher.fetch(kind)
                    .orElseThrow(() -> new IllegalArgumentException("Schema not found: " + kind));
        }
    }
}
