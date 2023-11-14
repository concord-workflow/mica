package org.acme.mica.server.api.model;

import java.util.Map;

public record ClientProfile(String name, Map<String, Object> schema) {

    public static String KIND = "MicaProfile/v1";
}
