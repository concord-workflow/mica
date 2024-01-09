package ca.ibodrov.mica.api.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

public record RenderRequest(Optional<EntityId> viewId,
        Optional<String> viewName,
        int limit,
        Optional<JsonNode> parameters) {

    public static RenderRequest of(String viewName, int limit) {
        return new RenderRequest(Optional.empty(), Optional.of(viewName), limit, Optional.empty());
    }

    public static RenderRequest parameterized(String viewName, JsonNode parameters, int limit) {
        return new RenderRequest(Optional.empty(), Optional.of(viewName), limit, Optional.of(parameters));
    }
}
