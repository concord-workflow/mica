package org.acme.mica.server.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * @param id         external ID, becomes the client's name
 * @param properties arbitrary properties
 */
public record ClientDataEntry(String id,
        Map<String, Object> properties) {

    // update when the entry's structure changes in a backward-incompatible way.
    public static String KIND = "MicaClientDataEntry/v1";

    @JsonCreator
    ClientDataEntry(@JsonProperty("id") String id) {
        this(id, new HashMap<>());
    }

    @JsonAnySetter
    private void updateProperty(String name, Object value) {
        properties.put(name, value);
    }
}
