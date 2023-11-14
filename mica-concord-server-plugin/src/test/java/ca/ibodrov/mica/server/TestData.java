package ca.ibodrov.mica.server;

import ca.ibodrov.mica.api.model.ClientDataEntry;
import org.jooq.Configuration;

import java.util.UUID;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_CLIENTS;
import static ca.ibodrov.mica.db.jooq.Tables.MICA_CLIENT_DATA;
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

    private TestData() {
    }
}
