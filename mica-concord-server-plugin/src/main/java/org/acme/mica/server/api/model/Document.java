package org.acme.mica.server.api.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Document {

    private final Optional<String> kind;
    private final Map<String, Object> data;

    @JsonCreator
    public Document(@JsonProperty("kind") @Nullable String kind) {
        this.kind = Optional.ofNullable(kind);
        this.data = new HashMap<>();
    }

    public Document(Optional<String> kind, Map<String, Object> data) {
        this.kind = kind;
        this.data = Map.copyOf(data);
    }

    public Optional<String> getKind() {
        return kind;
    }

    @JsonAnyGetter
    public Map<String, Object> getData() {
        return data;
    }

    @JsonAnySetter
    public void updateData(String name, Object value) {
        data.put(name, value);
    }
}
