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
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.*;
import com.networknt.schema.resource.InputStreamSource;
import com.networknt.schema.resource.SchemaLoader;
import com.networknt.schema.serialization.DefaultJsonNodeReader;

import java.io.InputStream;
import java.net.URI;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

@SuppressWarnings("ClassCanBeRecord")
public class Validator {

    public static Validator getDefault(ObjectMapper objectMapper, SchemaFetcher schemaFetcher) {
        return new Validator(JsonSchemaFactory.builder()
                .jsonNodeReader(DefaultJsonNodeReader.builder()
                        .jsonNodeFactoryFactory(jsonParser -> objectMapper.getNodeFactory()).jsonMapper(objectMapper)
                        .yamlMapper(objectMapper.copyWith(new YAMLFactory()))
                        .build())
                .defaultMetaSchemaIri(JsonMetaSchema.getV202012().getIri())
                .metaSchema(JsonMetaSchema.getV202012())
                .schemaLoaders(schemaLoaders -> schemaLoaders.add(new MicaSchemaLoader(schemaFetcher)))
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

    private static class MicaSchemaLoader implements SchemaLoader {
        private final SchemaFetcher schemaFetcher;

        public MicaSchemaLoader(SchemaFetcher schemaFetcher) {
            this.schemaFetcher = requireNonNull(schemaFetcher);
        }

        @Override
        public InputStreamSource getSchema(AbsoluteIri iri) {
            URI uri;
            try {
                uri = URI.create(iri.toString());
            } catch (IllegalArgumentException e) {
                return null;
            }

            // Handle json-schema.org resources
            if (uri.getHost() != null && uri.getHost().equals("json-schema.org")
                    && uri.getPath().startsWith("/draft/2020-12")) {
                var in = Validator.class.getResourceAsStream(uri.getPath());
                if (in != null) {
                    return () -> in;
                }
            }

            // Handle mica scheme
            if (!"mica".equals(uri.getScheme())) {
                return null;
            }

            if (!"internal".equals(uri.getHost())) {
                return null;
            }

            String kind = uri.getPath();
            return schemaFetcher.fetch(kind)
                    .map(inputStream -> (InputStreamSource) () -> inputStream)
                    .orElse(null);
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

}
