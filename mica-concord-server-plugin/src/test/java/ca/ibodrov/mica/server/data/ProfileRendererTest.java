package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.Profile;
import ca.ibodrov.mica.schema.Input;
import ca.ibodrov.mica.schema.ObjectSchemaNode;
import ca.ibodrov.mica.server.data.ProfileRenderer.EffectiveProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProfileRendererTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new Jdk8Module());

    @Test
    public void testSimpleRender() {
        var profile = Profile.of("test", p("""
                {
                    "properties": {
                        "username": {
                            "type": "string"
                        }
                    }
                }
                """));

        var renderer = new ProfileRenderer(objectMapper);
        var effectiveProfile = renderer.render(profile, new Input(Map.of("username", "bob", "role", "builder")));
        assertEquals("test", effectiveProfile.profileName());
        assertEquals(1, effectiveProfile.properties().size());
        assertValidProperty(effectiveProfile, "username", TextNode.valueOf("bob"));
    }

    private static void assertValidProperty(EffectiveProfile effectiveProfile, String key, JsonNode expectedValue) {
        assertTrue(effectiveProfile.properties().containsKey(key));
        assertTrue(effectiveProfile.properties().get(key).value().map(v -> v.equals(expectedValue)).orElse(false));
        assertTrue(effectiveProfile.properties().get(key).error().isEmpty());
    }

    private ObjectSchemaNode p(@Language("JSON") String s) {
        try {
            return objectMapper.readValue(s, ObjectSchemaNode.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
