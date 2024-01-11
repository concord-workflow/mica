package ca.ibodrov.mica.server.exceptions;

import ca.ibodrov.mica.api.model.ApiError;
import com.walmartlabs.concord.server.sdk.rest.Component;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

@Provider
public class ViewProcessorExceptionMapper implements Component, ExceptionMapper<ViewProcessorException> {

    @Override
    public Response toResponse(ViewProcessorException exception) {
        return Response.status(BAD_REQUEST)
                .type(APPLICATION_JSON)
                .entity(ApiError.badRequest(exception.getMessage()))
                .build();
    }
}
