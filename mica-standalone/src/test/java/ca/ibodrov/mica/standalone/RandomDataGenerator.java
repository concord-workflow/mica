package ca.ibodrov.mica.standalone;

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
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.server.data.EntityStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Inject;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class RandomDataGenerator {

    private static final Logger log = LoggerFactory.getLogger(RandomDataGenerator.class);

    private static final TypeReference<Map<String, JsonNode>> MAP_OF_JSON_NODES = new TypeReference<>() {
    };

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;
    private final EntityStore entityStore;

    @Inject
    public RandomDataGenerator(@MicaDB DSLContext dsl, ObjectMapper objectMapper, EntityStore entityStore) {
        this.dsl = requireNonNull(dsl);
        this.objectMapper = requireNonNull(objectMapper);
        this.entityStore = requireNonNull(entityStore);
    }

    public void generate(int numberOfEntities) {
        var counter = new AtomicLong(0);
        var stream = Stream.generate(() -> {
            var idx = counter.getAndIncrement();

            var name = "/entity-%s".formatted(idx);

            var path = "/random";
            if (idx % 10 == 1) {
                path += "/nested";
            }

            var fqn = path + name;
            var kind = "/mica/record/v1";

            var doc = """
                    name: %s
                    kind: %s
                    data:
                      foo: %s
                      bar:
                        baz:
                          qux: true
                      someArray: [1, 2, 3]
                    """.formatted(fqn, kind, "foo-" + idx);

            return new Entry(idx, PartialEntity.create(fqn, kind, parseYaml(doc)), doc);
        });

        stream.limit(numberOfEntities)
                .collect(Collectors.groupingBy(e -> e.idx / 1000)).values()
                .forEach(batch -> {
                    log.info("Inserting next batch...");
                    dsl.transaction(cfg -> {
                        var tx = cfg.dsl();
                        for (var entry : batch) {
                            entityStore.upsert(tx, entry.entity, entry.doc);
                        }
                    });
                });
    }

    private Map<String, JsonNode> parseYaml(String s) {
        try {
            return objectMapper.copyWith(new YAMLFactory()).readValue(s, MAP_OF_JSON_NODES);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private record Entry(long idx, PartialEntity entity, String doc) {
    }
}
