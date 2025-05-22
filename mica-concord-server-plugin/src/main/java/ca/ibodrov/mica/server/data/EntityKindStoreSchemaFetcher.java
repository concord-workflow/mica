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

import ca.ibodrov.mica.server.data.Validator.SchemaFetcher;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class EntityKindStoreSchemaFetcher implements SchemaFetcher {

    private final EntityKindStore entityKindStore;
    private final ObjectMapper objectMapper;

    public EntityKindStoreSchemaFetcher(EntityKindStore entityKindStore, ObjectMapper objectMapper) {
        this.entityKindStore = requireNonNull(entityKindStore);
        this.objectMapper = requireNonNull(objectMapper);
    }

    @Override
    public Optional<InputStream> fetch(String kind) {
        return entityKindStore.getSchemaForKind(kind).map(schema -> {
            try {
                // TODO is there a way to avoid serialization here
                return objectMapper.writeValueAsBytes(schema);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).map(ByteArrayInputStream::new);
    }
}
