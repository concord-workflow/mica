package ca.ibodrov.mica.server.api;

/*-
 * ~~~~~~
 * Mica
 * ------
 * Copyright (C) 2023 - 2025 Mica Authors
 * ------
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ======
 */

import ca.ibodrov.mica.api.model.DataExport;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.IOUtils;
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

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;

@Tag(name = "Data Import")
@Path("/api/mica/v1/import")
public class ImportResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(ImportResource.class);

    private final DSLContext concordDsl;
    private final DSLContext micaDsl;
    private final ObjectMapper objectMapper;

    @Inject
    public ImportResource(@MainDB Configuration mainCfg,
                          @MicaDB Configuration micaCfg,
                          ObjectMapper objectMapper) {

        this.concordDsl = requireNonNull(mainCfg).dsl();
        this.micaDsl = requireNonNull(micaCfg).dsl();
        this.objectMapper = requireNonNull(objectMapper);
    }

    @POST
    @WithTimer
    @Operation(summary = "Import data as a ZIP archive", operationId = "importDataZip")
    @Consumes(APPLICATION_OCTET_STREAM)
    @Produces(APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    public ImportResponse importData(InputStream in, @QueryParam("confirmation") String confirmation) {
        if (!"it_might_destroy_existing_data".equals(confirmation)) {
            throw new IllegalArgumentException(
                    "Requires confirmation. Pass 'confirmation=it_might_destroy_existing_data' query parameter.");
        }

        java.nio.file.Path tmpDir = null;
        try {
            tmpDir = IOUtils.createTempDir("import");
            IOUtils.unzip(in, tmpDir);

            var exportFile = tmpDir.resolve("export.json");
            if (!Files.exists(exportFile) || !Files.isRegularFile(exportFile)) {
                throw ApiException.badRequest("Expected to find an export.json file in the ZIP archive.");
            }

            try (var reader = Files.newBufferedReader(exportFile)) {
                var dataExport = objectMapper.readValue(reader, DataExport.class);
                doImport(dataExport);
                return new ImportResponse(true);
            }
        } catch (Exception e) {
            log.error("importData -> error", e);
            throw ApiException.internalError("Failed to import a ZIP archive: " + e.getMessage());
        } finally {
            if (tmpDir != null) {
                try {
                    IOUtils.deleteRecursively(tmpDir);
                } catch (IOException e) {
                    log.warn("Failed to delete the temporary directory: {}", tmpDir);
                }
            }
        }
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

        try {
            doImport(dataExport);
        } catch (Exception e) {
            log.error("importData -> error", e);
            throw ApiException.internalError("Failed to import JSON: " + e.getMessage());
        }

        return new ImportResponse(true);
    }

    private void doImport(DataExport dataExport) {
        log.info("doImport -> start");

        concordDsl.transaction(cfg -> {
            var tx = cfg.dsl();

            Optional.ofNullable(dataExport.concordUsers()).orElse(List.of())
                    .forEach(r -> upsert(tx, USERS, r));

            Optional.ofNullable(dataExport.concordOrganizations()).orElse(List.of())
                    .forEach(r -> upsert(tx, ORGANIZATIONS, r));

            Optional.ofNullable(dataExport.concordProjects()).orElse(List.of())
                    .forEach(r -> upsert(tx, PROJECTS, r));

            Optional.ofNullable(dataExport.concordSecrets()).orElse(List.of())
                    .forEach(r -> upsert(tx, SECRETS, r));

            Optional.ofNullable(dataExport.concordRepositories()).orElse(List.of())
                    .forEach(r -> upsert(tx, REPOSITORIES, r));

            Optional.ofNullable(dataExport.concordProjectSecrets()).orElse(List.of())
                    .forEach(r -> insert(tx, PROJECT_SECRETS, r));

            Optional.ofNullable(dataExport.concordJsonStores()).orElse(List.of())
                    .forEach(r -> upsert(tx, JSON_STORES, r));

            Optional.ofNullable(dataExport.concordJsonStoreQueries()).orElse(List.of())
                    .forEach(r -> upsert(tx, JSON_STORE_QUERIES, r));

            Optional.ofNullable(dataExport.concordJsonStoreData()).orElse(List.of())
                    .forEach(r -> upsert(tx, JSON_STORE_DATA, r));

            Optional.ofNullable(dataExport.concordTeams()).orElse(List.of())
                    .forEach(r -> upsert(tx, TEAMS, r));

            Optional.ofNullable(dataExport.concordProjectTeamAccess()).orElse(List.of())
                    .forEach(r -> upsert(tx, PROJECT_TEAM_ACCESS, r));

            Optional.ofNullable(dataExport.concordSecretTeamAccess()).orElse(List.of())
                    .forEach(r -> upsert(tx, SECRET_TEAM_ACCESS, r));

            Optional.ofNullable(dataExport.concordJsonStoreTeamAccess()).orElse(List.of())
                    .forEach(r -> upsert(tx, JSON_STORE_TEAM_ACCESS, r));
        });

        micaDsl.transaction(cfg -> {
            var tx = cfg.dsl();

            tx.truncateTable(MICA_ENTITIES).execute();
            Optional.ofNullable(dataExport.micaEntities()).orElse(List.of())
                    .forEach(r -> upsert(tx, MICA_ENTITIES, r));

            tx.truncateTable(MICA_ENTITY_HISTORY).execute();
            Optional.ofNullable(dataExport.micaEntityHistory()).orElse(List.of())
                    .forEach(r -> upsert(tx, MICA_ENTITY_HISTORY, r));
        });

        log.info("doImport -> done");
    }

    public record ImportResponse(boolean ok) {
    }

    private static boolean exists(DSLContext tx, TableRecord<?> r) {
        var table = r.getTable();

        // check the primary key first

        var pk = table.getPrimaryKey();
        if (pk != null && exists(tx, table, r, pk)) {
            return true;
        }

        // check if there are other unique keys we should consider

        for (var uk : table.getKeys()) {
            if (!uk.isPrimary() && exists(tx, table, r, uk)) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean exists(DSLContext tx,
                                  Table<? extends TableRecord<?>> table,
                                  TableRecord<?> r,
                                  UniqueKey<?> uk) {
        Condition condition = null;
        for (var field : uk.getFields()) {
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
