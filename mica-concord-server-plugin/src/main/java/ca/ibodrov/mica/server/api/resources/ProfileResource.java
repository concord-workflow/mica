package ca.ibodrov.mica.server.api.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.server.api.model.Profile;
import org.hibernate.validator.constraints.Length;
import org.jooq.Configuration;
import org.jooq.impl.DSL;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.ws.rs.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static ca.ibodrov.mica.db.jooq.Tables.MICA_PROFILES;

@Path("/api/mica/v1/profile")
@Produces(APPLICATION_JSON)
public class ProfileResource implements Resource {

    private final Configuration cfg;
    private final ObjectMapper objectMapper;

    @Inject
    public ProfileResource(@MicaDB Configuration cfg, ObjectMapper objectMapper) {
        this.cfg = cfg;
        this.objectMapper = objectMapper;
    }

    @GET
    public ProfileList listProfiles(@QueryParam("search") @Length(max = 128) String search) {
        var searchCondition = Optional.ofNullable(search)
                .map(MICA_PROFILES.NAME::containsIgnoreCase)
                .orElseGet(DSL::noCondition);

        var data = cfg.dsl()
                .select(MICA_PROFILES.ID, MICA_PROFILES.NAME)
                .from(MICA_PROFILES)
                .where(searchCondition)
                .fetch(r -> new ProfileEntry(r.get(MICA_PROFILES.ID), r.get(MICA_PROFILES.NAME)));

        return new ProfileList(data);
    }

    @GET
    @Path("{name}")
    public Profile getProfile(@PathParam("name") String name) {
        return cfg.dsl()
                .selectFrom(MICA_PROFILES)
                .where(MICA_PROFILES.NAME.eq(name))
                .fetchOptional(r -> new Profile(Optional.of(r.getId()),
                        r.getName(),
                        Optional.of(r.getCreatedAt()),
                        parseSchema(r.getSchema())))
                .orElseThrow(() -> new WebApplicationException(NOT_FOUND));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSchema(String schema) {
        try {
            return objectMapper.readValue(schema, Map.class);
        } catch (Exception e) {
            throw new WebApplicationException(INTERNAL_SERVER_ERROR);
        }
    }

    public record ProfileList(List<ProfileEntry> data) {
    }

    public record ProfileEntry(UUID id, String name) {
    }
}
