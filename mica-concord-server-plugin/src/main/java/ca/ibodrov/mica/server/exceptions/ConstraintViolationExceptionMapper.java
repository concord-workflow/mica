package ca.ibodrov.mica.server.exceptions;

import org.sonatype.siesta.Component;

import javax.validation.ConstraintViolationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import java.util.stream.Collectors;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException>, Component {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        var message = exception.getConstraintViolations().stream()
                .map(error -> "%s: %s".formatted(error.getPropertyPath(), error.getMessage()))
                .collect(Collectors.joining("\n"));
        return Response.status(BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ValidationErrors(message))
                .build();
    }

    public record ValidationErrors(String message) {
    }
}
