package ca.ibodrov.mica.server.exceptions;

import org.jooq.exception.DataAccessException;
import org.sonatype.siesta.Component;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

public class DataAccessExceptionMapper implements Component, ExceptionMapper<DataAccessException> {

    @Override
    public Response toResponse(DataAccessException exception) {
        return Response.serverError()
                .entity(new InternalError(exception.getMessage()))
                .build();
    }

    public record InternalError(String message) {
    }
}
