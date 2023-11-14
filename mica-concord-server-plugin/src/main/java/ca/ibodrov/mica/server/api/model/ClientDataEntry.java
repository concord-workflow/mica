package ca.ibodrov.mica.server.api.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

public class ClientDataEntry {

    // update when the entry's structure changes in a backward-incompatible way.
    public static String KIND = "MicaClientDataEntry/v1";

    private final String id;
    private final Map<String, Object> properties;

    @JsonCreator
    public ClientDataEntry(@JsonProperty("id") String id) {
        this(id, new HashMap<>());
    }

    public ClientDataEntry(String id, Map<String, Object> properties) {
        this.id = id;
        this.properties = properties;
    }

    public String getId() {
        return id;
    }

    @JsonAnyGetter
    public Map<String, Object> getProperties() {
        return properties;
    }

    @JsonAnySetter
    private void updateProperty(String name, Object value) {
        properties.put(name, value);
    }
}
