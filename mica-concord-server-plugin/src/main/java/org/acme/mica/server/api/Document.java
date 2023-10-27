package org.acme.mica.server.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Document(List<ClientDataEntry> clients) {
}
