package ca.ibodrov.mica.server.api.resources;

import ca.ibodrov.mica.api.model.ClientEndpoint;
import ca.ibodrov.mica.api.model.ClientEndpointList;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.server.data.ClientEndpointController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hibernate.validator.constraints.Length;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import java.util.Optional;
import java.util.UUID;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_CLIENTS;
import static ca.ibodrov.mica.db.jooq.Tables.MICA_CLIENT_ENDPOINTS;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.jooq.impl.DSL.select;

@Tag(name = "ClientEndpoint")
@Path("/api/mica/v1/clientEndpoint")
public class ClientEndpointResource implements Resource {

    private final DSLContext dsl;
    private final ClientEndpointController controller;

    @Inject
    public ClientEndpointResource(@MicaDB DSLContext dsl, ClientEndpointController controller) {
        this.dsl = dsl;
        this.controller = controller;
    }

    @GET
    @Produces(APPLICATION_JSON)
    @Operation(description = "List known client endpoints", operationId = "listClientEndpoints")
    public ClientEndpointList listClientEndpoints(@QueryParam("search") @Length(max = 128) String search) {
        var clientName = select(MICA_CLIENTS.NAME).from(MICA_CLIENTS)
                .where(MICA_CLIENTS.ID.eq(MICA_CLIENT_ENDPOINTS.CLIENT_ID))
                .asField();

        var searchCondition = Optional.ofNullable(search)
                .map(substring -> MICA_CLIENT_ENDPOINTS.ENDPOINT_URI.containsIgnoreCase(substring)
                        .or(MICA_CLIENT_ENDPOINTS.LAST_KNOWN_STATUS.containsIgnoreCase(substring)))
                .orElseGet(DSL::noCondition);

        var data = dsl.select(MICA_CLIENT_ENDPOINTS.ID,
                MICA_CLIENT_ENDPOINTS.CLIENT_ID,
                clientName,
                MICA_CLIENT_ENDPOINTS.ENDPOINT_URI,
                MICA_CLIENT_ENDPOINTS.LAST_KNOWN_STATUS,
                MICA_CLIENT_ENDPOINTS.STATUS_UPDATED_AT)
                .from(MICA_CLIENT_ENDPOINTS)
                .where(searchCondition)
                .fetchInto(ClientEndpoint.class);

        return new ClientEndpointList(data);
    }

    @POST
    @Path("importFromClientData")
    @Consumes(APPLICATION_JSON)
    @Operation(description = "Import client endpoints using client data", operationId = "importFromClientData")
    public void importFromClientData(@Valid ImportRequest request) {
        controller.importFromClientData(request.profileId());
    }

    public record ImportRequest(@NotNull UUID profileId) {
    }
}
