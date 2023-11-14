package ca.ibodrov.mica.server.api;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

public class ApiException extends WebApplicationException {

    public static ApiException badRequest(Throwable cause) {
        return new ApiException(BAD_REQUEST, cause);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(BAD_REQUEST, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(NOT_FOUND, message);
    }

    public ApiException(Status status, Throwable cause) {
        super(cause, Response.status(status)
                .entity(new ErrorResponse(cause.getMessage()))
                .build());
    }

    public ApiException(Status status, String message) {
        super(Response.status(status)
                .entity(new ErrorResponse(message))
                .build());
    }

    public record ErrorResponse(String message) {
    }
}
