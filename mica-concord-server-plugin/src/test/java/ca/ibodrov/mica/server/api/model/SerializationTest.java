package ca.ibodrov.mica.server.api.model;

import ca.ibodrov.mica.api.model.*;
import ca.ibodrov.mica.schema.ObjectSchemaNode;
import ca.ibodrov.mica.schema.ValidatedProperty;
import ca.ibodrov.mica.server.data.ProfileRenderer.EffectiveProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SerializationTest {

    private final ObjectMapper mapper = new ObjectMapperProvider().get();

    @Test
    public void testParseClientDataDocument() throws Exception {
        var document = new Document(Optional.of(ClientDataDocument.KIND),
                Map.of("clients",
                        List.of(
                                new ClientDataEntry("id1", Map.of("foo", "bar")),
                                new ClientDataEntry("id2", Map.of("baz", "qux")))));

        var map = mapper.convertValue(document, Map.class);
        var json = mapper.writeValueAsString(map);
        var clientList = mapper.readValue(json, ClientDataDocument.class);
        assertEquals(2, clientList.clients().size());
    }

    @Test
    public void testRoundTripProfiles() throws Exception {
        var profile1 = new Profile(ProfileId.of(UUID.randomUUID()), "foo",
                Optional.of(OffsetDateTime.now(ZoneOffset.UTC)), ObjectSchemaNode.emptyObject());
        var json = mapper.writeValueAsString(profile1);
        var profile2 = mapper.readValue(json, Profile.class);
        assertEquals(profile1, profile2);
    }

    @Test
    public void testRoundTripEffectiveProfiles() throws Exception {
        var profile1 = new EffectiveProfile("foo",
                Map.of("bar", ValidatedProperty.valid(TextNode.valueOf("baz"))));
        var str = mapper.writeValueAsString(profile1);
        var profile2 = mapper.readValue(str, EffectiveProfile.class);
        assertEquals(profile1, profile2);
    }

    @Test
    public void testIdWrappers() throws Exception {
        var entityId1 = new EntityId(UUID.randomUUID());
        var json1 = """
                { "id": "%s" }
                """.formatted(entityId1.toExternalForm());
        var entityIdContainer = mapper.readValue(json1, EntityIdContainer.class);
        assertEquals(entityId1, entityIdContainer.id());
    }

    public record EntityIdContainer(EntityId id) {
    }
}
