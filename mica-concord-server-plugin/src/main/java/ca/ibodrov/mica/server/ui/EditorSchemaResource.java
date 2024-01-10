package ca.ibodrov.mica.server.ui;

import ca.ibodrov.mica.api.validation.ValidName;
import ca.ibodrov.mica.schema.ObjectSchemaNode;
import ca.ibodrov.mica.server.data.EntityKindStore;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/api/mica/ui/editorSchema")
@Produces(APPLICATION_JSON)
public class EditorSchemaResource implements Resource {

    private final EntityKindStore entityKindStore;

    @Inject
    public EditorSchemaResource(EntityKindStore entityKindStore) {
        this.entityKindStore = requireNonNull(entityKindStore);
    }

    @GET
    @Validate
    public ObjectSchemaNode getSchemaForEntityKind(@ValidName @QueryParam("kind") String kind) {
        return entityKindStore.getSchemaForKind(kind)
                .orElseThrow(() -> ApiException.notFound("Schema not found: " + kind));
    }
}
