package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityId;
import ca.ibodrov.mica.api.model.EntityVersion;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.server.UuidGenerator;
import ca.ibodrov.mica.server.api.ApiException;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import javax.inject.Inject;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_ENTITIES;
import static java.util.Objects.requireNonNull;
import static org.jooq.JSONB.jsonb;
import static org.jooq.impl.DSL.currentOffsetDateTime;

public class EntityController {

    private final DSLContext dsl;
    private final UuidGenerator uuidGenerator;

    @Inject
    public EntityController(@MicaDB DSLContext dsl, UuidGenerator uuidGenerator) {
        this.dsl = requireNonNull(dsl);
        this.uuidGenerator = requireNonNull(uuidGenerator);
    }

    public EntityVersion createOrUpdate(PartialEntity entity) {
        if (entity.id().isEmpty()) {
            var alreadyExists = dsl.fetchExists(MICA_ENTITIES, MICA_ENTITIES.NAME.eq(entity.name()));
            if (alreadyExists) {
                throw ApiException.badRequest("Entity with name '%s' already exists".formatted(entity.name()));
            }
        }

        var id = entity.id().map(EntityId::id)
                .orElseGet(uuidGenerator::generate);

        var data = jsonb(entity.data().toString());

        var row = dsl.transactionResult(tx -> tx.dsl().insertInto(MICA_ENTITIES)
                .set(MICA_ENTITIES.ID, id)
                .set(MICA_ENTITIES.NAME, entity.name())
                .set(MICA_ENTITIES.KIND, entity.kind())
                .set(MICA_ENTITIES.DATA, data)
                .onConflict(MICA_ENTITIES.ID)
                .doUpdate()
                .set(MICA_ENTITIES.NAME, entity.name())
                .set(MICA_ENTITIES.KIND, entity.kind())
                .set(MICA_ENTITIES.DATA, data)
                .set(MICA_ENTITIES.UPDATED_AT, currentOffsetDateTime())
                .where(entity.updatedAt().map(MICA_ENTITIES.UPDATED_AT::eq).orElseGet(DSL::noCondition))
                .returning(MICA_ENTITIES.UPDATED_AT)
                .fetchOptional());

        if (row.isEmpty()) {
            throw ApiException.conflict("Version conflict: " + entity.name());
        }

        return new EntityVersion(new EntityId(id), row.get().getUpdatedAt());
    }
}
