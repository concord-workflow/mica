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

import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.server.YamlMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Responsible for loading the initial data set, e.g. the default entity kinds
 * and such.
 */
public class InitialDataLoader {

    private static final Logger log = LoggerFactory.getLogger(InitialDataLoader.class);

    private final DSLContext dsl;
    private final EntityStore entityStore;
    private final ObjectMapper objectMapper;

    @Inject
    public InitialDataLoader(@MicaDB DSLContext dsl,
                             EntityStore entityStore,
                             ObjectMapper objectMapper) {

        this.dsl = requireNonNull(dsl);
        this.entityStore = requireNonNull(entityStore);
        this.objectMapper = requireNonNull(objectMapper);

        // no out of the box support for @PostConstruct in Guice
        load();
    }

    public void load() {
        // load example files
        loadPackage("ca.ibodrov.mica.server.examples");
        // load other stuff
        loadPackage("ca.ibodrov.mica.server.entities");
    }

    private void loadPackage(String packageName) {
        var cl = getClass().getClassLoader();
        var yamlMapper = new YamlMapper(objectMapper);
        var reflections = new Reflections(packageName, new ResourcesScanner());
        reflections.getResources(s -> s.endsWith(".yaml")).forEach(resourceName -> {
            try (var in = cl.getResourceAsStream(resourceName)) {
                assert in != null;
                var doc = new String(in.readAllBytes(), UTF_8);
                var entity = yamlMapper.readValue(doc, PartialEntity.class);
                createOrReplace(entity, doc);
            } catch (IOException e) {
                throw new RuntimeException("Error loading " + resourceName, e);
            }
        });
    }

    private void createOrReplace(PartialEntity entity, String doc) {
        dsl.transaction(cfg -> {
            var tx = cfg.dsl();

            entityStore.getByName(tx, entity.name())
                    .flatMap(existingEntity -> entityStore.killById(tx, existingEntity.id()))
                    .ifPresent(deleted -> log.info("Removed old version of {}: {}", entity.name(), deleted));

            entityStore.upsert(tx, entity, doc);
        });

        log.info("Created or replaced an entity: {}", entity.name());
    }
}
