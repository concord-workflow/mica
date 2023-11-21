package ca.ibodrov.mica.server.api.resources;

import ca.ibodrov.mica.api.model.EntityPutResult;
import ca.ibodrov.mica.api.model.Entity;
import ca.ibodrov.mica.server.api.ApiException;
import ca.ibodrov.mica.server.data.EntityController;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.io.IOException;
import java.io.InputStream;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/api/mica/v1/entity")
@Produces(APPLICATION_JSON)
public class EntityResource implements Resource {

    private final EntityController controller;
    private final ObjectMapper yamlMapper;

    @Inject
    public EntityResource(EntityController controller) {
        this.controller = controller;
        this.yamlMapper = new ObjectMapper(new YAMLFactory())
                .registerModule(new Jdk8Module())
                .registerModule(new GuavaModule())
                .registerModule(new JavaTimeModule());
    }

    @PUT
    @Consumes("*/yaml")
    public EntityPutResult putYaml(InputStream in) {
        Entity entity;
        try {
            entity = yamlMapper.readValue(in, Entity.class);
        } catch (IOException e) {
            throw ApiException.badRequest("Error parsing YAML: " + e.getMessage());
        }
        return controller.putEntity(entity);
    }
}
