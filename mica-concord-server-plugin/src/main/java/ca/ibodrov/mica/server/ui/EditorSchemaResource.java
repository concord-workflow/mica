package ca.ibodrov.mica.server.ui;

import ca.ibodrov.mica.api.validation.ValidName;
import ca.ibodrov.mica.server.data.EntityKindStore;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/api/mica/ui/editorSchema")
@Produces(APPLICATION_JSON)
public class EditorSchemaResource implements Resource {

    private static final TypeReference<Map<String, Object>> MAP_OF_OBJECTS = new TypeReference<>() {
    };

    private final EntityKindStore entityKindStore;
    private final ObjectMapper objectMapper;

    @Inject
    public EditorSchemaResource(EntityKindStore entityKindStore,
                                ObjectMapper objectMapper) {

        this.entityKindStore = requireNonNull(entityKindStore);
        this.objectMapper = requireNonNull(objectMapper);
    }

    @GET
    @Validate
    public Map<String, Object> getSchemaForEntityKind(@ValidName @QueryParam("kind") String kind) {
        var schema = entityKindStore.getSchemaForKind(kind)
                .orElseThrow(() -> ApiException.notFound("Schema not found: " + kind));

        // ObjectMapper used in REST resources is configured to
        // JsonInclude.Include.NON_NULL
        // and for some reason ObjectSchemaNode's @JsonInclude(NON_ABSENT) doesn't work
        return objectMapper.convertValue(schema, MAP_OF_OBJECTS);
    }
}
