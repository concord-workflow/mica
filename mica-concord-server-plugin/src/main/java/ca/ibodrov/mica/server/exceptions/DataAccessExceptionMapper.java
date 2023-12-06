package ca.ibodrov.mica.server.exceptions;

import ca.ibodrov.mica.api.model.ApiError;
import org.jooq.exception.DataAccessException;
import org.sonatype.siesta.Component;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class DataAccessExceptionMapper implements Component, ExceptionMapper<DataAccessException> {

    @Override
    public Response toResponse(DataAccessException exception) {
        return Response.serverError()
                .type(APPLICATION_JSON)
                .entity(ApiError.internalError(exception.getMessage()))
                .build();
    }
}
