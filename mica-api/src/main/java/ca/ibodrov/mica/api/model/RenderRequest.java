package ca.ibodrov.mica.api.model;

import ca.ibodrov.mica.api.validation.ValidName;
import com.fasterxml.jackson.databind.JsonNode;

import javax.validation.ConstraintViolationException;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public record RenderRequest(Optional<EntityId> viewId,
        Optional<String> viewName,
        int limit,
        Optional<JsonNode> parameters) {

    public RenderRequest {
        requireNonNull(viewId);
        requireNonNull(viewName);

        if (viewId.isEmpty() && viewName.isEmpty()) {
            throw new ConstraintViolationException("One of viewId or viewName must be set", Set.of());
        }

        viewName.ifPresent(name -> {
            if (!name.matches(ValidName.NAME_PATTERN)) {
                throw new ConstraintViolationException("Invalid view name: " + name + ". " + ValidName.MESSAGE,
                        Set.of());
            }
        });
    }

    public static RenderRequest of(String viewName, int limit) {
        return new RenderRequest(Optional.empty(), Optional.of(viewName), limit, Optional.empty());
    }

    public static RenderRequest parameterized(String viewName, JsonNode parameters, int limit) {
        return new RenderRequest(Optional.empty(), Optional.of(viewName), limit, Optional.ofNullable(parameters));
    }
}
