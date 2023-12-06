package ca.ibodrov.mica.server.exceptions;

import ca.ibodrov.mica.api.model.ApiError;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.*;

public class ApiException extends WebApplicationException {

    public static ApiException badRequest(String message) {
        return new ApiException(BAD_REQUEST, ApiError.badRequest(message));
    }

    public static ApiException notFound(String message) {
        return new ApiException(NOT_FOUND, ApiError.notFound(message));
    }

    public static ApiException internalError(String message) {
        return new ApiException(INTERNAL_SERVER_ERROR, ApiError.internalError(message));
    }

    public static ApiException conflict(String message) {
        return new ApiException(CONFLICT, ApiError.conflict(message));
    }

    public ApiException(Status status, ApiError error) {
        this(status, error, null);
    }

    public ApiException(Status status, ApiError error, Throwable cause) {
        super(cause, Response.status(status)
                .type(APPLICATION_JSON)
                .entity(error)
                .build());
    }

    public Status getStatus() {
        return Status.fromStatusCode(getResponse().getStatus());
    }
}
