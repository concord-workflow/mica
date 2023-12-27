package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.db.MicaDB;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.net.URI;
import java.util.stream.Stream;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_ENTITIES;
import static java.util.Objects.requireNonNull;

public class InternalEntityFetcher implements EntityFetcher {

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    @Inject
    public InternalEntityFetcher(@MicaDB DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = requireNonNull(dsl);
        this.objectMapper = requireNonNull(objectMapper);
    }

    // TODO move into a separate class?
    @Override
    public Stream<EntityLike> getAllByKind(URI uri, String kind, int limit) {
        if (!uri.getScheme().equals("mica") && !uri.getPath().equals("internal")) {
            return Stream.empty();
        }

        var step = dsl.select(MICA_ENTITIES.ID,
                MICA_ENTITIES.NAME,
                MICA_ENTITIES.KIND,
                MICA_ENTITIES.CREATED_AT,
                MICA_ENTITIES.UPDATED_AT,
                MICA_ENTITIES.DATA)
                .from(MICA_ENTITIES)
                .where(MICA_ENTITIES.KIND.eq(kind));

        if (limit > 0) {
            step.limit(limit);
        }

        return step.fetchStream().map(r -> EntityStore.toEntity(objectMapper, r));
    }
}
