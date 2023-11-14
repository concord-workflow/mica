package org.acme.mica.server.api.resources;

import org.acme.mica.db.MicaDB;
import org.hibernate.validator.constraints.Length;
import org.jooq.Configuration;
import org.jooq.impl.DSL;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.acme.mica.db.jooq.Tables.CLIENT_PROFILES;

@Path("/api/mica/v1/clientProfile")
public class ClientProfileResource implements Resource {

    private final Configuration cfg;

    @Inject
    public ClientProfileResource(@MicaDB Configuration cfg) {
        this.cfg = cfg;
    }

    @GET
    @Produces(APPLICATION_JSON)
    public ClientProfileList listProfiles(@QueryParam("search") @Length(max = 128) String search) {
        var searchCondition = Optional.ofNullable(search)
                .map(CLIENT_PROFILES.NAME::containsIgnoreCase)
                .orElseGet(DSL::noCondition);

        var data = cfg.dsl()
                .select(CLIENT_PROFILES.ID, CLIENT_PROFILES.NAME)
                .from(CLIENT_PROFILES)
                .where(searchCondition)
                .fetch(r -> new ClientProfileEntry(r.get(CLIENT_PROFILES.ID), r.get(CLIENT_PROFILES.NAME)));

        return new ClientProfileList(data);
    }

    public record ClientProfileList(List<ClientProfileEntry> data) {
    }

    public record ClientProfileEntry(UUID id, String name) {
    }
}
