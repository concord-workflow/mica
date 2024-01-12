package ca.ibodrov.mica.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import javax.validation.constraints.NotEmpty;
import java.util.Optional;

/**
 * All exceptions thrown by the API should be mapped to this class.
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record ApiError(@NotEmpty String type, @NotEmpty String message, Optional<JsonNode> payload) {

    public static ApiError notFound(String message) {
        return new ApiError("not-found", message, Optional.empty());
    }

    public static ApiError badRequest(String message) {
        return new ApiError("bad-request", message, Optional.empty());
    }

    public static ApiError conflict(String message) {
        return new ApiError("conflict", message, Optional.empty());
    }

    public static ApiError internalError(String message) {
        return new ApiError("internal-error", message, Optional.empty());
    }
}
