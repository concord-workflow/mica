package ca.ibodrov.mica.server.api.model;

import ca.ibodrov.mica.api.model.EntityId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.junit.jupiter.api.Test;
import org.sonatype.siesta.jackson2.ObjectMapperProvider;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SerializationTest {

    private final ObjectMapper mapper = new ObjectMapperProvider().get()
            .registerModule(new Jdk8Module());

    @Test
    public void testIdWrappers() throws Exception {
        var entityId1 = new EntityId(UUID.randomUUID());
        var json1 = """
                { "id": "%s" }
                """.formatted(entityId1.toExternalForm());
        var entityIdContainer = mapper.readValue(json1, EntityIdContainer.class);
        assertEquals(entityId1, entityIdContainer.id());
    }

    @Test
    public void testEmptyJsonNodes() throws Exception {
        var json = """
                {}
                """;
        var result = mapper.readValue(json, OptionalJsonNodeContainer.class);
        assertEquals(Optional.of(NullNode.getInstance()), result.node());
    }

    public record EntityIdContainer(EntityId id) {
    }

    public record OptionalJsonNodeContainer(Optional<JsonNode> node) {
    }
}
