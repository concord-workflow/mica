package ca.ibodrov.mica.api.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public interface EntityLike {

    String name();

    String kind();

    Map<String, JsonNode> data();
}
