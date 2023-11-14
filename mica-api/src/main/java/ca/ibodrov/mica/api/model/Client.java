package ca.ibodrov.mica.api.model;

import ca.ibodrov.mica.api.validation.ValidClientName;

import javax.validation.constraints.NotEmpty;
import java.util.Map;
import java.util.UUID;

public record Client(@NotEmpty UUID id,
        @ValidClientName String name,
        Map<String, Object> properties) {
}
