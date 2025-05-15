package ca.ibodrov.mica.server.api;

import ca.ibodrov.mica.api.model.DataExport;
import ca.ibodrov.mica.db.MicaDB;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jooq.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.*;
import java.util.Map;

import static ca.ibodrov.mica.db.jooq.tables.MicaEntities.MICA_ENTITIES;
import static ca.ibodrov.mica.db.jooq.tables.MicaEntityHistory.MICA_ENTITY_HISTORY;
import static com.walmartlabs.concord.server.jooq.tables.JsonStoreData.JSON_STORE_DATA;
import static com.walmartlabs.concord.server.jooq.tables.JsonStoreQueries.JSON_STORE_QUERIES;
import static com.walmartlabs.concord.server.jooq.tables.JsonStoreTeamAccess.JSON_STORE_TEAM_ACCESS;
import static com.walmartlabs.concord.server.jooq.tables.JsonStores.JSON_STORES;
import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.ProjectSecrets.PROJECT_SECRETS;
import static com.walmartlabs.concord.server.jooq.tables.ProjectTeamAccess.PROJECT_TEAM_ACCESS;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.tables.Repositories.REPOSITORIES;
import static com.walmartlabs.concord.server.jooq.tables.SecretTeamAccess.SECRET_TEAM_ACCESS;
import static com.walmartlabs.concord.server.jooq.tables.Secrets.SECRETS;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;
import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Tag(name = "Data Import")
@Path("/api/mica/v1/import")
public class ImportResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(ImportResource.class);

    private final DSLContext concordDsl;
    private final DSLContext micaDsl;

    @Inject
    public ImportResource(@MainDB Configuration mainCfg, @MicaDB Configuration micaCfg) {
        this.concordDsl = requireNonNull(mainCfg).dsl();
        this.micaDsl = requireNonNull(micaCfg).dsl();
    }

    @POST
    @WithTimer
    @Operation(summary = "Import data", operationId = "importData")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public ImportResponse importData(DataExport dataExport, @QueryParam("confirmation") String confirmation) {
        if (!Roles.isAdmin()) {
            throw new UnauthorizedException("Only users with the admin role can import data.");
        }

        if (!"it_might_destroy_existing_data".equals(confirmation)) {
            throw new IllegalArgumentException(
                    "Requires confirmation. Pass 'confirmation=it_might_destroy_existing_data' query parameter.");
        }

        concordDsl.transaction(cfg -> {
            var tx = cfg.dsl();

            dataExport.concordUsers()
                    .forEach(r -> upsert(tx, USERS, r));

            dataExport.concordOrganizations()
                    .forEach(r -> upsert(tx, ORGANIZATIONS, r));

            dataExport.concordProjects()
                    .forEach(r -> upsert(tx, PROJECTS, r));

            dataExport.concordSecrets()
                    .forEach(r -> upsert(tx, SECRETS, r));

            dataExport.concordRepositories()
                    .forEach(r -> upsert(tx, REPOSITORIES, r));

            dataExport.concordProjectSecrets()
                    .forEach(r -> insert(tx, PROJECT_SECRETS, r));

            dataExport.concordJsonStores()
                    .forEach(r -> upsert(tx, JSON_STORES, r));

            dataExport.concordJsonStoreQueries()
                    .forEach(r -> upsert(tx, JSON_STORE_QUERIES, r));

            dataExport.concordJsonStoreData()
                    .forEach(r -> upsert(tx, JSON_STORE_DATA, r));

            dataExport.concordTeams()
                    .forEach(r -> upsert(tx, TEAMS, r));

            dataExport.concordProjectTeamAccess()
                    .forEach(r -> upsert(tx, PROJECT_TEAM_ACCESS, r));

            dataExport.concordSecretTeamAccess()
                    .forEach(r -> upsert(tx, SECRET_TEAM_ACCESS, r));

            dataExport.concordJsonStoreTeamAccess()
                    .forEach(r -> upsert(tx, JSON_STORE_TEAM_ACCESS, r));
        });

        micaDsl.transaction(cfg -> {
            var tx = cfg.dsl();

            tx.truncateTable(MICA_ENTITIES).execute();
            dataExport.micaEntities()
                    .forEach(r -> upsert(tx, MICA_ENTITIES, r));

            tx.truncateTable(MICA_ENTITY_HISTORY).execute();
            dataExport.micaEntityHistory()
                    .forEach(r -> upsert(tx, MICA_ENTITY_HISTORY, r));
        });

        return new ImportResponse(true);
    }

    public record ImportResponse(boolean ok) {
    }

    @SuppressWarnings("unchecked")
    private static boolean exists(DSLContext tx, TableRecord<?> r) {
        var table = r.getTable();

        var pk = table.getPrimaryKey();
        if (pk == null) {
            throw new IllegalStateException(table + " doesn't have a primary key");
        }

        Condition condition = null;
        for (var field : pk.getFields()) {
            var val = r.get(field);
            var fieldCondition = ((Field<Object>) field).eq(val);
            condition = condition == null ? fieldCondition : condition.and(fieldCondition);
        }

        return tx.fetchExists(tx.selectOne().from(table).where(condition));
    }

    private static void insert(DSLContext tx, Table<? extends TableRecord<?>> t, Map<String, Object> m) {
        var r = tx.newRecord(t);
        r.fromMap(m);
        if (!exists(tx, r)) {
            r.insert();
        }
    }

    private static void upsert(DSLContext tx, Table<? extends UpdatableRecord<?>> t, Map<String, Object> m) {
        var r = tx.newRecord(t);
        r.fromMap(m);
        if (exists(tx, r)) {
            r.update();
        } else {
            r.insert();
        }
    }
}
