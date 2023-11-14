package org.acme.mica.server.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.db.MainDB;
import org.acme.mica.server.UuidGenerator;
import org.acme.mica.server.api.model.Profile;
import org.acme.mica.server.api.model.Document;
import org.jooq.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;

import static org.acme.mica.db.jooq.Tables.MICA_PROFILES;

public class ProfileImporter implements DocumentImporter {

    private static final Logger log = LoggerFactory.getLogger(ProfileImporter.class);

    private final Configuration cfg;
    private final UuidGenerator uuidGenerator;
    private final ObjectMapper objectMapper;

    @Inject
    public ProfileImporter(@MainDB Configuration cfg, UuidGenerator uuidGenerator) {
        this.cfg = cfg;
        this.uuidGenerator = uuidGenerator;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public boolean canImport(Document doc) {
        return doc.getKind().map(kind -> kind.equals(Profile.KIND)).orElse(false);
    }

    @Override
    public void importDocument(Document doc) {
        var profile = objectMapper.convertValue(doc.getData(), Profile.class);

        String schema;
        try {
            schema = objectMapper.writeValueAsString(profile.schema());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        var id = uuidGenerator.generate();
        cfg.dsl().transaction(cfg -> cfg.dsl()
                .insertInto(MICA_PROFILES)
                .columns(MICA_PROFILES.ID, MICA_PROFILES.NAME, MICA_PROFILES.KIND, MICA_PROFILES.SCHEMA)
                .values(id, profile.name(), Profile.KIND, schema)
                .execute());

        log.info("Inserted a new profile, id={}, name={}", id, profile.name());
    }
}
