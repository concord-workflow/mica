package ca.ibodrov.mica.server.api;

import ca.ibodrov.mica.api.model.BatchOperationRequest;
import ca.ibodrov.mica.api.model.BatchOperationResult;
import ca.ibodrov.mica.server.data.EntityStore;
import ca.ibodrov.mica.server.exceptions.ApiException;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Tag(name = "Batch Entity Operations")
@Path("/api/mica/v1/batch")
@Produces(APPLICATION_JSON)
public class BatchOperationResource implements Resource {

    private final EntityStore entityStore;

    @Inject
    public BatchOperationResource(EntityStore entityStore) {
        this.entityStore = requireNonNull(entityStore);
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Validate
    public BatchOperationResult apply(@Valid BatchOperationRequest request) {
        assert request.operation() != null;
        switch (request.operation()) {
            case DELETE -> {
                var namePatterns = request.namePatterns()
                        .orElseThrow(() -> ApiException.badRequest("Missing 'namePatterns'"));
                if (namePatterns.isEmpty()) {
                    throw new IllegalArgumentException("Empty 'namePatterns'");
                }
                var deletedEntities = entityStore.deleteByNamePatterns(namePatterns);
                return new BatchOperationResult(Optional.of(deletedEntities));
            }
            default -> throw ApiException.badRequest("Unsupported operation: " + request.operation());
        }
    }
}
