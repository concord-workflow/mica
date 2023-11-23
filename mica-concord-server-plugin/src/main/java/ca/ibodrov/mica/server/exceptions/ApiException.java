package ca.ibodrov.mica.server.exceptions;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.*;

public class ApiException extends WebApplicationException {

    private final ErrorKind errorKind;

    public static ApiException badRequest(ErrorKind errorKind, Throwable cause) {
        return new ApiException(errorKind, BAD_REQUEST, cause);
    }

    public static ApiException badRequest(ErrorKind errorKind, String message) {
        return new ApiException(errorKind, BAD_REQUEST, message);
    }

    public static ApiException notFound(ErrorKind errorKind, String message) {
        return new ApiException(errorKind, NOT_FOUND, message);
    }

    public static ApiException internalError(String message) {
        return new ApiException(ErrorKind.INTERNAL_ERROR, INTERNAL_SERVER_ERROR, message);
    }

    public static ApiException conflict(ErrorKind errorKind, String message) {
        return new ApiException(errorKind, CONFLICT, message);
    }

    public ApiException(ErrorKind errorKind, Status status, Throwable cause) {
        super(cause, Response.status(status)
                .type(APPLICATION_JSON)
                .entity(new ErrorResponse(cause.getMessage()))
                .build());
        this.errorKind = errorKind;
    }

    public ApiException(ErrorKind errorKind, Status status, String message) {
        super(message, Response.status(status)
                .type(APPLICATION_JSON)
                .entity(new ErrorResponse(message))
                .build());
        this.errorKind = errorKind;
    }

    public Status getStatus() {
        return Status.fromStatusCode(getResponse().getStatus());
    }

    public ErrorKind getErrorKind() {
        return errorKind;
    }

    public record ErrorResponse(String message) {
    }

    public enum ErrorKind {
        INTERNAL_ERROR,
        BAD_DATA,
        NO_DATA,
        UNKNOWN_ENTITY_KIND
    }
}
