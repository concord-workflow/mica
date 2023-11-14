package ca.ibodrov.mica.server.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientDataDocument(List<ClientDataEntry> clients) {

    @JsonCreator
    public ClientDataDocument {
    }

    public static String KIND = "MicaClientData/v1";
}
