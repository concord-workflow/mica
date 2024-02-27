package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.db.MicaDB;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.net.URI;

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

    @Override
    public boolean isSupported(URI uri) {
        return "mica".equals(uri.getScheme()) && "internal".equals(uri.getHost());
    }

    @Override
    public Cursor getAllByKind(URI uri, String kind, int limit) {
        var step = dsl.select(MICA_ENTITIES.ID,
                MICA_ENTITIES.NAME,
                MICA_ENTITIES.KIND,
                MICA_ENTITIES.CREATED_AT,
                MICA_ENTITIES.UPDATED_AT,
                MICA_ENTITIES.DATA)
                .from(MICA_ENTITIES)
                .where(MICA_ENTITIES.KIND.likeRegex(kind));

        if (limit > 0) {
            step.limit(limit);
        }

        var cursor = step.fetch(r -> (EntityLike) EntityStore.toEntity(objectMapper, r)).stream();
        return () -> cursor;
    }
}
