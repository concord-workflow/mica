package org.acme.mica.server.api;

import com.fasterxml.jackson.annotation.JsonRawValue;
import org.acme.mica.db.MicaDB;
import org.jooq.Configuration;
import org.jooq.impl.DSL;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.acme.mica.db.jooq.Tables.CLIENT_DATA;

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
    public ClientData getLatestData(@QueryParam("externalId") @ValidClientName String externalId) {
        return DSL.using(cfg)
                .select(CLIENT_DATA.PARSED_DATA).from(CLIENT_DATA)
                .where(CLIENT_DATA.EXTERNAL_ID.eq(externalId))
                .orderBy(CLIENT_DATA.IMPORTED_AT.desc())
                .limit(1)
                .fetchOptional(record -> new ClientData(record.value1().data()))
                .orElseGet(() -> new ClientData("{}"));
    }

    public record ClientData(@JsonRawValue String properties) {
    }
}
