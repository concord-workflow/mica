package ca.ibodrov.mica.api.model;

import com.fasterxml.jackson.databind.JsonNode;

import javax.validation.constraints.NotNull;
import java.util.Optional;

public record PreviewRequest(@NotNull PartialEntity view, int limit, Optional<JsonNode> parameters) {
}
