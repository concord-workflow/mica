package ca.ibodrov.mica.server.api.resources;

import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.schema.ObjectSchemaNode;
import ca.ibodrov.mica.server.api.ApiException;
import ca.ibodrov.mica.api.model.Profile;
import ca.ibodrov.mica.api.model.ProfileId;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hibernate.validator.constraints.Length;
import org.jooq.Configuration;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.ws.rs.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_PROFILES;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Tag(name = "profile")
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
    @Operation(description = "List known profiles", operationId = "listProfiles")
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
    @Operation(description = "Get profile by name", operationId = "getProfile")
    public Profile getProfile(@PathParam("name") String name) {
        var profile = cfg.dsl()
                .selectFrom(MICA_PROFILES)
                .where(MICA_PROFILES.NAME.eq(name))
                .fetchOptional(r -> new Profile(ProfileId.of(r.getId()),
                        r.getName(),
                        Optional.of(r.getCreatedAt()),
                        parseIntoSchema(r.getSchema())))
                .orElseThrow(() -> ApiException.notFound("Profile not found: " + name));
        return profile;
    }

    private ObjectSchemaNode parseIntoSchema(JSONB json) {
        try {
            return objectMapper.readValue(json.data(), ObjectSchemaNode.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public record ProfileList(List<ProfileEntry> data) {
    }

    public record ProfileEntry(UUID id, String name) {
    }
}
