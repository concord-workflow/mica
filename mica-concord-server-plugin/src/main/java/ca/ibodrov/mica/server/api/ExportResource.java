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

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.Map;

import static ca.ibodrov.mica.db.jooq.tables.MicaEntities.MICA_ENTITIES;
import static ca.ibodrov.mica.db.jooq.tables.MicaEntityHistory.MICA_ENTITY_HISTORY;
import static com.walmartlabs.concord.server.jooq.tables.ApiKeys.API_KEYS;
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

@Tag(name = "Data Export")
@Path("/api/mica/v1/export")
@Produces(APPLICATION_JSON)
public class ExportResource implements Resource {

    private final DSLContext concordDsl;
    private final DSLContext micaDsl;

    @Inject
    public ExportResource(@MainDB Configuration mainCfg, @MicaDB Configuration micaCfg) {
        this.concordDsl = requireNonNull(mainCfg).dsl();
        this.micaDsl = requireNonNull(micaCfg).dsl();
    }

    @GET
    @WithTimer
    @Operation(summary = "Export data", operationId = "exportData")
    public DataExport exportData() {
        if (!Roles.isAdmin()) {
            throw new UnauthorizedException("Only users with the admin role can export data.");
        }

        return new DataExport(
                concordDsl.selectFrom(ORGANIZATIONS).fetch().map(ExportResource::toSerializableMap),
                concordDsl.selectFrom(PROJECTS).fetch().map(ExportResource::toSerializableMap),
                concordDsl.selectFrom(REPOSITORIES).fetch().map(ExportResource::toSerializableMap),
                concordDsl.selectFrom(SECRETS).fetchMaps(),
                concordDsl.selectFrom(PROJECT_SECRETS).fetchMaps(),
                concordDsl.selectFrom(JSON_STORES).fetchMaps(),
                concordDsl.selectFrom(JSON_STORE_QUERIES).fetchMaps(),
                concordDsl.selectFrom(JSON_STORE_DATA).fetch().map(ExportResource::toSerializableMap),
                concordDsl.selectFrom(USERS).fetchMaps(),
                concordDsl.selectFrom(TEAMS).fetchMaps(),
                concordDsl.selectFrom(PROJECT_TEAM_ACCESS).fetchMaps(),
                concordDsl.selectFrom(SECRET_TEAM_ACCESS).fetchMaps(),
                concordDsl.selectFrom(JSON_STORE_TEAM_ACCESS).fetchMaps(),
                concordDsl.selectFrom(API_KEYS).fetchMaps(),
                micaDsl.selectFrom(MICA_ENTITIES).fetch().map(ExportResource::toSerializableMap),
                micaDsl.selectFrom(MICA_ENTITY_HISTORY).fetchMaps());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toSerializableMap(TableRecord<?> r) {
        var m = r.intoMap();
        for (var field : r.fields()) {
            if ("jsonb".equals(field.getDataType().getTypeName())) {
                var jsonbField = (Field<JSONB>) field;
                var jsonb = r.get(jsonbField);
                if (jsonb != null) {
                    m.put(jsonbField.getName(), r.get(jsonbField).data());
                }
            }
        }
        return m;
    }
}
