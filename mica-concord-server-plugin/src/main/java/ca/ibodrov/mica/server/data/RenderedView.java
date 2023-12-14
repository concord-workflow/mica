package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.ViewLike;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record RenderedView(ViewLike view, List<JsonNode> data) {
}
