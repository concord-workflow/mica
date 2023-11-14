package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.server.UuidGenerator;
import ca.ibodrov.mica.api.model.ClientDataDocument;
import ca.ibodrov.mica.api.model.Document;
import com.fasterxml.jackson.databind.ObjectMapper;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.api.model.ClientDataEntry;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.UUID;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_CLIENTS;
import static ca.ibodrov.mica.db.jooq.Tables.MICA_CLIENT_DATA;
import static org.jooq.JSONB.jsonb;

public class ClientDataImporter implements DocumentImporter {

    private static final Logger log = LoggerFactory.getLogger(ClientDataImporter.class);

    private final Configuration cfg;
    private final UuidGenerator uuidGenerator;
    private final ObjectMapper objectMapper;

    @Inject
    public ClientDataImporter(@MicaDB Configuration cfg, UuidGenerator uuidGenerator) {
        this.cfg = cfg;
        this.uuidGenerator = uuidGenerator;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public boolean canImport(Document doc) {
        return doc.getKind().map(kind -> kind.equals(ClientDataDocument.KIND))
                .orElseGet(() -> doc.getData().containsKey("clients"));
    }

    @Override
    public void importDocument(Document document) {
        // TODO validate ClientDataEntry#id() format
        var clientList = objectMapper.convertValue(document, ClientDataDocument.class);

        var documentId = uuidGenerator.generate();
        log.info("Importing a new client data document with {} client(s), documentId={}", clientList.clients().size(),
                documentId);
        cfg.dsl().transaction(cfg -> {
            var tx = cfg.dsl();
            clientList.clients()
                    .forEach(client -> insert(tx, documentId, client));
        });
    }

    private void insert(DSLContext tx, UUID documentId, ClientDataEntry client) {
        JSONB data;
        try {
            data = jsonb(objectMapper.writeValueAsString(client));
        } catch (IOException e) {
            throw new RuntimeException("JSON serialization error: " + e.getMessage());
        }
        tx.insertInto(MICA_CLIENT_DATA)
                .columns(MICA_CLIENT_DATA.DOCUMENT_ID, MICA_CLIENT_DATA.KIND, MICA_CLIENT_DATA.EXTERNAL_ID,
                        MICA_CLIENT_DATA.PARSED_DATA)
                .values(documentId, ClientDataEntry.KIND, client.getId(), data)
                .execute();

        var clientId = uuidGenerator.generate();
        var clientName = client.getId();

        int rows = tx.insertInto(MICA_CLIENTS).columns(MICA_CLIENTS.ID, MICA_CLIENTS.NAME)
                .values(clientId, clientName)
                .onConflictDoNothing()
                .execute();
        if (rows > 0) {
            log.info("Inserted a new client entry, clientId={}, clientName={}", clientId, clientName);
        }
    }
}
