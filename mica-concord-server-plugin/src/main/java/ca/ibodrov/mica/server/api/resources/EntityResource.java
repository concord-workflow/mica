package ca.ibodrov.mica.server.api.resources;

import ca.ibodrov.mica.api.model.Entity;
import ca.ibodrov.mica.api.model.EntityVersion;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.server.api.ApiException;
import ca.ibodrov.mica.server.data.EntityController;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jooq.DSLContext;
import org.sonatype.siesta.Resource;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_ENTITIES;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.jooq.impl.DSL.noCondition;

@Path("/api/mica/v1/entity")
@Produces(APPLICATION_JSON)
public class EntityResource implements Resource {

    private final DSLContext dsl;
    private final EntityController controller;
    private final ObjectMapper yamlMapper;

    @Inject
    public EntityResource(@MicaDB DSLContext dsl, EntityController controller) {
        this.dsl = dsl;
        this.controller = controller;
        this.yamlMapper = new ObjectMapper(new YAMLFactory())
                .registerModule(new Jdk8Module())
                .registerModule(new GuavaModule())
                .registerModule(new JavaTimeModule());
    }

    @GET
    public EntityList listEntities(@Nullable @QueryParam("search") String search) {
        var searchCondition = search != null ? MICA_ENTITIES.NAME.containsIgnoreCase(search) : noCondition();
        var data = dsl.selectFrom(MICA_ENTITIES).where(searchCondition).fetchInto(Entity.class);
        return new EntityList(data);
    }

    @PUT
    @Consumes("*/yaml")
    public EntityVersion putYaml(InputStream in) {
        Entity entity;
        try {
            entity = yamlMapper.readValue(in, Entity.class);
        } catch (IOException e) {
            throw ApiException.badRequest("Error parsing YAML: " + e.getMessage());
        }
        return controller.putEntity(entity);
    }

    public record EntityList(List<Entity> data) {
    }
}
