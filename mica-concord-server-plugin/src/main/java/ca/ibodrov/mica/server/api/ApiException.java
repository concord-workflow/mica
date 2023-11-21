package ca.ibodrov.mica.server.api;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.*;

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

    public static ApiException internalError(String message) {
        return new ApiException(INTERNAL_SERVER_ERROR, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(CONFLICT, message);
    }

    public ApiException(Status status, Throwable cause) {
        super(cause, Response.status(status)
                .type(APPLICATION_JSON)
                .entity(new ErrorResponse(cause.getMessage()))
                .build());
    }

    public ApiException(Status status, String message) {
        super(message, Response.status(status)
                .type(APPLICATION_JSON)
                .entity(new ErrorResponse(message))
                .build());
    }

    public Status getStatus() {
        return Status.fromStatusCode(getResponse().getStatus());
    }

    public record ErrorResponse(String message) {
    }
}
