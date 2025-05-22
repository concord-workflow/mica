package ca.ibodrov.mica.server.data;

/*-
 * ~~~~~~
 * Mica
 * ------
 * Copyright (C) 2023 - 2025 Mica Authors
 * ------
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ======
 */

import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.uri.URIFactory;
import com.networknt.schema.uri.URIFetcher;

import java.io.InputStream;
import java.net.URI;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

@SuppressWarnings("ClassCanBeRecord")
public class Validator {

    private static final String[] ALL_MICA_SCHEMES = { "http", "https", "mica" };

    public static Validator getDefault(ObjectMapper objectMapper, SchemaFetcher schemaFetcher) {
        return new Validator(JsonSchemaFactory.builder()
                .objectMapper(objectMapper)
                .defaultMetaSchemaURI(JsonMetaSchema.getV202012().getUri())
                .addMetaSchema(JsonMetaSchema.getV202012())
                .uriFactory(new URIFactoryImpl(), ALL_MICA_SCHEMES)
                .uriFetcher(new URIFetcherImpl(schemaFetcher), ALL_MICA_SCHEMES)
                .build());
    }

    private final JsonSchemaFactory jsonSchemaFactory;

    public JsonSchemaFactory getJsonSchemaFactory() {
        return jsonSchemaFactory;
    }

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

    private static class URIFactoryImpl implements URIFactory {

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
        Optional<InputStream> fetch(String kind);
    }

    public static class NoopSchemaFetcher implements SchemaFetcher {

        @Override
        public Optional<InputStream> fetch(String kind) {
            return Optional.empty();
        }
    }

    private record URIFetcherImpl(SchemaFetcher schemaFetcher) implements URIFetcher {

        private URIFetcherImpl {
            requireNonNull(schemaFetcher);
        }

        @Override
        public InputStream fetch(URI uri) {
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
