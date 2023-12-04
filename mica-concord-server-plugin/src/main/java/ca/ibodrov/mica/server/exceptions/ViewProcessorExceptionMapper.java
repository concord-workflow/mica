package ca.ibodrov.mica.server.exceptions;

import ca.ibodrov.mica.server.data.ViewProcessorException;
import org.sonatype.siesta.Component;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

public class ViewProcessorExceptionMapper implements Component, ExceptionMapper<ViewProcessorException> {

    @Override
    public Response toResponse(ViewProcessorException exception) {
        return Response.status(BAD_REQUEST)
                .type(APPLICATION_JSON)
                .entity(new ViewError(exception.getMessage()))
                .build();
    }

    public record ViewError(String message) {
    }
}
