package ca.ibodrov.mica.server.exceptions;

import ca.ibodrov.mica.api.model.ApiError;
import com.walmartlabs.concord.server.sdk.rest.Component;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import java.util.Set;
import java.util.stream.Collectors;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException>, Component {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        // TODO return as a list of errors in the payload
        Set<ConstraintViolation<?>> violations = exception.getConstraintViolations();
        var message = violations.stream()
                .map(error -> "%s: %s".formatted(error.getPropertyPath(), error.getMessage()))
                .collect(Collectors.joining("\n"));
        return Response.status(BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(ApiError.simpleValidationError(message))
                .build();
    }
}
