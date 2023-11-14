package org.acme.mica.server.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.db.MainDB;
import org.acme.mica.server.UuidGenerator;
import org.acme.mica.server.api.model.ClientProfile;
import org.acme.mica.server.api.model.Document;
import org.jooq.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;

import static org.acme.mica.db.jooq.Tables.CLIENT_PROFILES;

public class ClientProfileImporter implements DocumentImporter {

    private static final Logger log = LoggerFactory.getLogger(ClientProfileImporter.class);

    private final Configuration cfg;
    private final UuidGenerator uuidGenerator;
    private final ObjectMapper objectMapper;

    @Inject
    public ClientProfileImporter(@MainDB Configuration cfg, UuidGenerator uuidGenerator) {
        this.cfg = cfg;
        this.uuidGenerator = uuidGenerator;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public boolean canImport(Document doc) {
        return doc.getKind().map(kind -> kind.equals(ClientProfile.KIND)).orElse(false);
    }

    @Override
    public void importDocument(Document doc) {
        var profile = objectMapper.convertValue(doc.getData(), ClientProfile.class);

        String schema;
        try {
            schema = objectMapper.writeValueAsString(profile.schema());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        var id = uuidGenerator.generate();
        cfg.dsl().transaction(cfg -> cfg.dsl()
                .insertInto(CLIENT_PROFILES)
                .columns(CLIENT_PROFILES.ID, CLIENT_PROFILES.NAME, CLIENT_PROFILES.KIND, CLIENT_PROFILES.SCHEMA)
                .values(id, profile.name(), ClientProfile.KIND, schema)
                .execute());

        log.info("Inserted a new profile, id={}, name={}", id, profile.name());
    }
}
