package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.db.MicaDB;
import com.walmartlabs.concord.server.sdk.BackgroundTask;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_ENTITIES;
import static java.util.Objects.requireNonNull;
import static org.jooq.impl.DSL.regexpReplaceAll;

public class EntityNormalizer implements BackgroundTask {

    private static final Logger log = LoggerFactory.getLogger(EntityNormalizer.class);

    private final DSLContext dsl;

    @Inject
    public EntityNormalizer(@MicaDB DSLContext dsl) {
        this.dsl = requireNonNull(dsl);
    }

    @Override
    public void start() {
        var rows = dsl.update(MICA_ENTITIES)
                .set(MICA_ENTITIES.NAME, regexpReplaceAll(MICA_ENTITIES.NAME, "//+", "/"))
                .where(MICA_ENTITIES.NAME.contains("//"))
                .execute();

        log.info("Normalized {} record(s)", rows);
    }
}
