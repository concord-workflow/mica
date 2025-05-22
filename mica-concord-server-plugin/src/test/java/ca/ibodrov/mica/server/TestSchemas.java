package ca.ibodrov.mica.server;

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

import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.data.BuiltinSchemas;
import ca.ibodrov.mica.server.data.Validator.SchemaFetcher;
import com.fasterxml.jackson.core.JsonFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Optional;

public final class TestSchemas {

    public static SchemaFetcher getBuiltinSchemaFetcher(YamlMapper yamlMapper) {
        return kind -> {
            switch (kind) {
                case BuiltinSchemas.STANDARD_PROPERTIES_V1 -> {
                    var path = "ca/ibodrov/mica/server/entities/mica-standard-properties-v1.yaml";
                    return fetchClassPathEntitySchema(yamlMapper, path);
                }
                default -> throw new IllegalArgumentException("Unsupported kind: " + kind);
            }
        };
    }

    private static Optional<InputStream> fetchClassPathEntitySchema(YamlMapper yamlMapper, String path) {
        try (var in = ClassLoader.getSystemClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }
            var entity = yamlMapper.readValue(new InputStreamReader(in), PartialEntity.class);
            var data = Optional.ofNullable(entity.getProperty("schema"))
                    .map(schema -> {
                        try {
                            return yamlMapper.getDelegate()
                                    .copyWith(new JsonFactory())
                                    .writeValueAsBytes(schema);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .orElseThrow(() -> new IllegalArgumentException("Expected a 'schema' in " + path));
            return Optional.of(new ByteArrayInputStream(data));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private TestSchemas() {
    }
}
