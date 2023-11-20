package ca.ibodrov.mica.server.api.resources;

import ca.ibodrov.mica.api.validation.ValidClientName;
import ca.ibodrov.mica.db.MicaDB;
import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jooq.Configuration;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_CLIENT_DATA;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Tag(name = "ClientData")
@Path("/api/mica/v1/clientData")
public class ClientDataResource implements Resource {

    private final Configuration cfg;

    @Inject
    public ClientDataResource(@MicaDB Configuration cfg) {
        this.cfg = cfg;
    }

    @GET
    @Path("latest")
    @Produces(APPLICATION_JSON)
    @Operation(description = "Returns the latest client data", operationId = "getLatestData")
    public ClientData getLatestData(@QueryParam("externalId") @ValidClientName String externalId) {
        return cfg.dsl()
                .select(MICA_CLIENT_DATA.PARSED_DATA).from(MICA_CLIENT_DATA)
                .where(MICA_CLIENT_DATA.EXTERNAL_ID.eq(externalId))
                .orderBy(MICA_CLIENT_DATA.IMPORTED_AT.desc())
                .limit(1)
                .fetchOptional(record -> new ClientData(record.value1().data()))
                .orElseGet(() -> new ClientData("{}"));
    }

    public record ClientData(@JsonRawValue String properties) {
    }
}
