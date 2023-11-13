package org.acme.mica.server.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonMapperTest {

    @Test
    public void test() throws Exception {
        var document = new Document(Optional.of(ClientDataDocument.KIND),
                Map.of("clients",
                        List.of(
                                new ClientDataEntry("id1", Map.of("foo", "bar")),
                                new ClientDataEntry("id2", Map.of("baz", "qux")))));

        var mapper = new ObjectMapper().registerModule(new Jdk8Module());
        var map = mapper.convertValue(document, Map.class);
        var text = mapper.writeValueAsString(map);
        System.out.println(text);

        var clientList = mapper.convertValue(document, ClientDataDocument.class);
        assertEquals(2, clientList.clients().size());
    }
}
