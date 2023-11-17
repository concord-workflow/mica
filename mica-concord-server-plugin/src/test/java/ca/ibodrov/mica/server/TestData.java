package ca.ibodrov.mica.server;

import ca.ibodrov.mica.api.model.ClientDataEntry;
import ca.ibodrov.mica.api.model.Profile;
import org.jooq.Configuration;

import java.util.UUID;

import static ca.ibodrov.mica.db.jooq.Tables.*;
import static org.jooq.JSONB.jsonb;

public final class TestData {

    public static void insertClient(Configuration tx, UUID id, String name) {
        tx.dsl().insertInto(MICA_CLIENTS)
                .columns(MICA_CLIENTS.ID, MICA_CLIENTS.NAME)
                .values(id, name)
                .execute();
    }

    public static void insertClientData(Configuration tx, UUID id, String externalId, String parsedData) {
        tx.dsl().insertInto(MICA_CLIENT_DATA)
                .columns(MICA_CLIENT_DATA.DOCUMENT_ID, MICA_CLIENT_DATA.EXTERNAL_ID, MICA_CLIENT_DATA.KIND,
                        MICA_CLIENT_DATA.PARSED_DATA)
                .values(id, externalId, ClientDataEntry.KIND, jsonb(parsedData))
                .execute();
    }

    public static void insertProfile(Configuration tx, UUID id, String name, String schema) {
        tx.dsl().insertInto(MICA_PROFILES)
                .columns(MICA_PROFILES.ID, MICA_PROFILES.NAME, MICA_PROFILES.KIND, MICA_PROFILES.SCHEMA)
                .values(id, name, Profile.KIND, jsonb(schema))
                .execute();
    }

    private TestData() {
    }
}
