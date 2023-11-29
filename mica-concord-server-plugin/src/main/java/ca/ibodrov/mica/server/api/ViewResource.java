package ca.ibodrov.mica.server.api;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.data.BuiltinSchemas;
import ca.ibodrov.mica.server.data.EntityStore;
import ca.ibodrov.mica.server.data.ViewProcessor;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import java.util.stream.Stream;

import static ca.ibodrov.mica.server.exceptions.ApiException.ErrorKind.NO_DATA;
import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/api/mica/v1/view")
@Produces(APPLICATION_JSON)
public class ViewResource implements Resource {

    private final EntityStore entityStore;
    private final ViewProcessor viewProcessor;

    @Inject
    public ViewResource(EntityStore entityStore, ObjectMapper objectMapper) {
        this.entityStore = requireNonNull(entityStore);
        this.viewProcessor = new ViewProcessor(requireNonNull(objectMapper));
    }

    @GET
    @Path("{viewName}/render")
    public PartialEntity render(@PathParam("viewName") String viewName) {
        var view = entityStore.getByName(viewName)
                .map(BuiltinSchemas::asView)
                .orElseThrow(() -> ApiException.notFound(NO_DATA, "View not found: " + viewName));
        var entities = Stream.<EntityLike>of();
        return viewProcessor.render(view, entities);
    }
}
