package ca.ibodrov.mica.server.exceptions;

import ca.ibodrov.mica.api.model.ApiError;
import com.walmartlabs.concord.server.sdk.rest.Component;
import org.jooq.exception.DataAccessException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Provider
public class DataAccessExceptionMapper implements Component, ExceptionMapper<DataAccessException> {

    @Override
    public Response toResponse(DataAccessException exception) {
        return Response.serverError()
                .type(APPLICATION_JSON)
                .entity(ApiError.internalError(exception.getMessage()))
                .build();
    }
}
