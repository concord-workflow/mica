package ca.ibodrov.mica.api.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Optional;

public interface EntityLike {

    String name();

    String kind();

    @JsonAnyGetter
    Map<String, JsonNode> data();

    Optional<EntityVersionAndName> versionAndName();
}
