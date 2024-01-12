package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.kinds.MicaViewV1;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.YamlMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import static ca.ibodrov.mica.api.kinds.MicaKindV1.MICA_KIND_V1;
import static ca.ibodrov.mica.api.kinds.MicaViewV1.MICA_VIEW_V1;
import static ca.ibodrov.mica.server.data.BuiltinSchemas.MICA_RECORD_V1;
import static java.util.Objects.requireNonNull;

/**
 * Responsible for loading the initial data set, e.g. the default entity kinds
 * and such.
 */
public class InitialDataLoader {

    private static final Logger log = LoggerFactory.getLogger(InitialDataLoader.class);

    private final BuiltinSchemas builtinSchemas;
    private final EntityStore entityStore;
    private final ObjectMapper objectMapper;

    @Inject
    public InitialDataLoader(BuiltinSchemas builtinSchemas,
                             EntityStore entityStore,
                             ObjectMapper objectMapper) {
        this.builtinSchemas = requireNonNull(builtinSchemas);
        this.entityStore = requireNonNull(entityStore);
        this.objectMapper = requireNonNull(objectMapper);

        // no out of the box support for @PostConstruct in Guice
        load();
    }

    public void load() {
        // built-in entity kinds
        createOrReplace(schema(MICA_KIND_V1, builtinSchemas.getMicaKindV1Schema()), null);
        createOrReplace(schema(MICA_RECORD_V1, builtinSchemas.getMicaRecordV1Schema()), null);
        createOrReplace(schema(MICA_VIEW_V1, builtinSchemas.getMicaViewV1Schema()), null);

        // load example files
        var cl = getClass().getClassLoader();
        var yamlMapper = new YamlMapper(objectMapper);
        var reflections = new Reflections("ca.ibodrov.mica.server.examples", new ResourcesScanner());
        reflections.getResources(s -> s.endsWith(".yaml")).forEach(resourceName -> {
            try (var in = cl.getResourceAsStream(resourceName)) {
                assert in != null;
                var doc = in.readAllBytes();
                var entity = yamlMapper.readValue(new ByteArrayInputStream(doc), PartialEntity.class);
                createOrReplace(entity, doc);
            } catch (IOException e) {
                throw new RuntimeException("Error loading " + resourceName, e);
            }
        });
    }

    private void createOrReplace(PartialEntity entity, byte[] doc) {
        entityStore.getByName(entity.name())
                .flatMap(existingEntity -> entityStore.deleteById(existingEntity.id()))
                .ifPresent(deleted -> log.info("Removed old version of {}: {}", entity.name(), deleted));

        entityStore.upsert(entity, doc);

        log.info("Created or replaced an entity: {}", entity.name());
    }

    private PartialEntity schema(String name, ObjectNode schema) {
        return PartialEntity.create(name, MICA_KIND_V1,
                Map.of("schema", schema));
    }

    private PartialEntity build(MicaViewV1.Builder builder) {
        return builder.build().toPartialEntity(objectMapper);
    }

    private PartialEntity record(String name, JsonNode data) {
        return PartialEntity.create(name, MICA_RECORD_V1,
                Map.of("data", data));
    }
}
