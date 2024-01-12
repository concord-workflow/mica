package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.server.data.Validator.SchemaFetcher;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class EntityKindStoreSchemaFetcher implements SchemaFetcher {

    private final EntityKindStore entityKindStore;
    private final ObjectMapper objectMapper;

    public EntityKindStoreSchemaFetcher(EntityKindStore entityKindStore, ObjectMapper objectMapper) {
        this.entityKindStore = requireNonNull(entityKindStore);
        this.objectMapper = requireNonNull(objectMapper);
    }

    @Override
    public Optional<InputStream> fetch(String kind) {
        return entityKindStore.getSchemaForKind(kind).map(schema -> {
            try {
                // TODO is there a way to avoid serialization here
                return objectMapper.writeValueAsBytes(schema);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).map(ByteArrayInputStream::new);
    }
}
