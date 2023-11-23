package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.PartialEntityKind;
import ca.ibodrov.mica.schema.ObjectSchemaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Map;
import java.util.Set;

import static ca.ibodrov.mica.schema.ObjectSchemaNode.*;

/**
 * Responsible for loading the initial data set, e.g. the default entity kinds
 * and such.
 */
public class InitialDataLoader {

    private static final Logger log = LoggerFactory.getLogger(InitialDataLoader.class);

    private final EntityKindStore entityKindStore;

    @Inject
    public InitialDataLoader(EntityKindStore entityKindStore) {
        this.entityKindStore = entityKindStore;

        // no out of the box support for @PostConstruct in Guice
        load();
    }

    public void load() {
        // TODO actual schemas
        var schema = object(Map.of(
                "id", string(),
                "kind", string(),
                "name", string(),
                "data", any()),
                Set.of("name", "kind", "data"));

        insertIfNotExists("MicaRecord/v1", schema);
        insertIfNotExists("MicaSchema/v1", schema);
        insertIfNotExists("MicaEntityTemplate/v1", schema);
        insertIfNotExists("MicaEntityView/v1", schema);
    }

    private void insertIfNotExists(String name, ObjectSchemaNode schema) {
        if (entityKindStore.isKindExists(name)) {
            return;
        }
        entityKindStore.upsert(PartialEntityKind.create(name, schema));
        log.info("Inserted an entity kind: {}", name);
    }
}
