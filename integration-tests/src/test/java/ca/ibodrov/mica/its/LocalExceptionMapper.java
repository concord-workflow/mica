package ca.ibodrov.mica.its;

import com.walmartlabs.concord.server.sdk.rest.Component;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

/**
 * Suppress noisy stack traces in the server logs.
 */
public class LocalExceptionMapper implements Component, ExceptionMapper<WebApplicationException> {

    @Override
    public Response toResponse(WebApplicationException exception) {
        return exception.getResponse();
    }
}
