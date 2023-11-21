package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.Document;
import ca.ibodrov.mica.api.model.Profile;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.schema.ObjectSchemaNode;
import ca.ibodrov.mica.server.UuidGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.db.MainDB;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_PROFILES;

public class ProfileImporter implements DocumentImporter {

    private static final Logger log = LoggerFactory.getLogger(ProfileImporter.class);

    private final DSLContext dsl;
    private final UuidGenerator uuidGenerator;
    private final ObjectMapper objectMapper;

    @Inject
    public ProfileImporter(@MicaDB DSLContext dsl, UuidGenerator uuidGenerator, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.uuidGenerator = uuidGenerator;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean canImport(Document doc) {
        return doc.getKind().map(kind -> kind.equals(Profile.KIND)).orElse(false);
    }

    @Override
    public void importDocument(Document doc) throws InvalidDocumentException {
        Profile profile;
        try {
            profile = objectMapper.convertValue(doc.getData(), Profile.class);
        } catch (Exception e) {
            throw new InvalidDocumentException(e.getMessage());
        }

        var id = uuidGenerator.generate();
        dsl.transaction(tx -> tx.dsl()
                .insertInto(MICA_PROFILES)
                .columns(MICA_PROFILES.ID, MICA_PROFILES.NAME, MICA_PROFILES.KIND, MICA_PROFILES.SCHEMA)
                .values(id, profile.name(), Profile.KIND, serializeAsJsonb(profile.schema()))
                .execute());

        log.info("Inserted a new profile, id={}, name={}", id, profile.name());
    }

    private JSONB serializeAsJsonb(ObjectSchemaNode schema) {
        try {
            return JSONB.valueOf(objectMapper.writeValueAsString(schema));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
