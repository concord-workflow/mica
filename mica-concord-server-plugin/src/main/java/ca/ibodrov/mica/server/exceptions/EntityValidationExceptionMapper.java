package ca.ibodrov.mica.server.exceptions;

import ca.ibodrov.mica.api.model.ApiError;
import ca.ibodrov.mica.schema.ValidationError;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.sonatype.siesta.Component;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

public class EntityValidationExceptionMapper implements ExceptionMapper<EntityValidationException>, Component {

    private final ObjectMapper objectMapper;

    @Inject
    public EntityValidationExceptionMapper(ObjectMapper objectMapper) {
        this.objectMapper = requireNonNull(objectMapper);
    }

    @Override
    public Response toResponse(EntityValidationException exception) {
        var payload = objectMapper.convertValue(new ErrorPayload(exception.getErrors()), JsonNode.class);
        return Response.status(BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(ApiError.detailedValidationError("Validation error", payload))
                .build();
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ErrorPayload(Map<String, ValidationError> errors) {
    }
}
