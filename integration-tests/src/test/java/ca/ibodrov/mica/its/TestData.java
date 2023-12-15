package ca.ibodrov.mica.its;

import org.jooq.Configuration;

import java.util.UUID;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_ENTITIES;
import static org.jooq.JSONB.jsonb;

public final class TestData {

    public static void insertEntity(Configuration tx, UUID id, String name, String kind, String data) {
        tx.dsl().insertInto(MICA_ENTITIES)
                .columns(MICA_ENTITIES.ID, MICA_ENTITIES.NAME, MICA_ENTITIES.KIND, MICA_ENTITIES.DATA)
                .values(id, name, kind, jsonb(data))
                .execute();
    }

    private TestData() {
    }
}
