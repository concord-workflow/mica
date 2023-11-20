package ca.ibodrov.mica.server.api.resources;

import ca.ibodrov.mica.api.model.Profile;
import ca.ibodrov.mica.api.model.ProfileId;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.schema.ObjectSchemaNode;
import ca.ibodrov.mica.server.UuidGenerator;
import ca.ibodrov.mica.server.api.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hibernate.validator.constraints.Length;
import org.jooq.Configuration;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import java.util.List;
import java.util.Optional;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_PROFILES;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.jooq.JSONB.jsonb;

@Tag(name = "profile")
@Path("/api/mica/v1/profile")
@Produces(APPLICATION_JSON)
public class ProfileResource implements Resource {

    private final Logger log = LoggerFactory.getLogger(ProfileResource.class);

    private final Configuration cfg;
    private final ObjectMapper objectMapper;
    private final UuidGenerator uuidGenerator;

    @Inject
    public ProfileResource(@MicaDB Configuration cfg,
                           ObjectMapper objectMapper,
                           UuidGenerator uuidGenerator) {

        this.cfg = cfg;
        this.objectMapper = objectMapper;
        this.uuidGenerator = uuidGenerator;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Operation(description = "Create a new profile", operationId = "createProfile")
    public ProfileEntry createProfile(@Valid NewProfile newProfile) {
        var id = uuidGenerator.generate();
        cfg.dsl().transaction(tx -> tx.dsl().insertInto(MICA_PROFILES)
                .columns(MICA_PROFILES.ID, MICA_PROFILES.NAME, MICA_PROFILES.KIND, MICA_PROFILES.SCHEMA)
                .values(id, newProfile.name(), Profile.KIND, serializeSchema(newProfile.schema()))
                .execute());

        log.info("Created a new profile, id={}, name={}", id, newProfile.name());

        return new ProfileEntry(new ProfileId(id), newProfile.name());
    }

    @PUT
    @Consumes(APPLICATION_JSON)
    @Operation(description = "Update an existing profile", operationId = "updateProfile")
    public ProfileEntry updateProfile(@Valid Profile profile) {
        var id = profile.id().orElseThrow(() -> ApiException.badRequest("Profile ID is missing"));

        cfg.dsl().transactionResult(tx -> tx.dsl().update(MICA_PROFILES)
                .set(MICA_PROFILES.NAME, profile.name())
                .set(MICA_PROFILES.SCHEMA, serializeSchema(profile.schema()))
                .where(MICA_PROFILES.ID.eq(id.id()))
                .execute());

        log.info("Updated a profile, id={}, name={}", id, profile.name());

        return new ProfileEntry(id, profile.name());
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
                .fetch(r -> new ProfileEntry(new ProfileId(r.get(MICA_PROFILES.ID)), r.get(MICA_PROFILES.NAME)));

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

    private JSONB serializeSchema(ObjectSchemaNode schema) {
        try {
            return jsonb(objectMapper.writeValueAsString(schema));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public record NewProfile(@NotEmpty String name, @NotNull ObjectSchemaNode schema) {
    }

    public record ProfileList(List<ProfileEntry> data) {
    }

    public record ProfileEntry(ProfileId id, String name) {
    }
}
