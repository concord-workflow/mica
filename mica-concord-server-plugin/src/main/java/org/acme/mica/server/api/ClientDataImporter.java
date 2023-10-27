package org.acme.mica.server.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.acme.mica.db.MicaDB;
import org.acme.mica.server.UuidGenerator;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.UUID;

import static org.acme.mica.db.jooq.Tables.CLIENTS;
import static org.acme.mica.db.jooq.Tables.CLIENT_DATA;
import static org.jooq.JSONB.jsonb;

public class ClientDataImporter {

    private static final Logger log = LoggerFactory.getLogger(ClientDataImporter.class);

    private final Configuration cfg;
    private final UuidGenerator uuidGenerator;
    private final ObjectMapper jsonMapper;

    @Inject
    public ClientDataImporter(@MicaDB Configuration cfg, UuidGenerator uuidGenerator) {
        this.cfg = cfg;
        this.uuidGenerator = uuidGenerator;
        this.jsonMapper = new ObjectMapper();
    }

    public ImportResult importClientData(Document document) {
        // TODO validate ClientDataEntry#id() format

        var documentId = uuidGenerator.generate();
        log.info("Importing a new client data document with {} client(s), documentId={}", document.clients().size(),
                documentId);
        DSL.using(cfg).transaction(cfg -> {
            var tx = DSL.using(cfg);
            document.clients()
                    .forEach(client -> insert(tx, documentId, client));
        });
        return new ImportResult(documentId);
    }

    private void insert(DSLContext tx, UUID documentId, ClientDataEntry client) {
        JSONB data;
        try {
            data = jsonb(jsonMapper.writeValueAsString(client));
        } catch (IOException e) {
            throw new ClientDataException("JSON serialization error: " + e.getMessage());
        }
        tx.insertInto(CLIENT_DATA)
                .columns(CLIENT_DATA.DOCUMENT_ID, CLIENT_DATA.KIND, CLIENT_DATA.EXTERNAL_ID, CLIENT_DATA.PARSED_DATA)
                .values(documentId, ClientDataEntry.KIND, client.id(), data)
                .execute();

        var clientId = uuidGenerator.generate();
        var clientName = client.id();

        int rows = tx.insertInto(CLIENTS).columns(CLIENTS.ID, CLIENTS.NAME)
                .values(clientId, clientName)
                .onConflictDoNothing()
                .execute();
        if (rows > 0) {
            log.info("Inserted a new client entry, clientId={}, clientName={}", clientId, clientName);
        }
    }

    public record ImportResult(UUID documentId) {
    }
}
