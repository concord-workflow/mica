package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.schema.ObjectSchemaNode;
import ca.ibodrov.mica.schema.ValidatedProperty;
import ca.ibodrov.mica.schema.Validator;
import ca.ibodrov.mica.server.UuidGenerator;
import ca.ibodrov.mica.server.api.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.db.MainDB;
import org.jooq.Configuration;
import org.jooq.JSONB;
import org.jooq.Record2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static ca.ibodrov.mica.db.jooq.Tables.*;

public class ClientEndpointController {

    private static final Logger log = LoggerFactory.getLogger(ClientEndpointController.class);

    private final Configuration cfg;
    private final ObjectMapper objectMapper;
    private final UuidGenerator uuidGenerator;

    @Inject
    public ClientEndpointController(@MainDB Configuration cfg,
                                    ObjectMapper objectMapper,
                                    UuidGenerator uuidGenerator) {
        this.cfg = cfg;
        this.objectMapper = objectMapper;
        this.uuidGenerator = uuidGenerator;
    }

    public void importFromClientData(UUID profileId) {
        assert profileId != null;

        var profile = cfg.dsl().selectFrom(MICA_PROFILES).where(MICA_PROFILES.ID.eq(profileId))
                .fetchOptional()
                .orElseThrow(() -> ApiException.badRequest("Unknown profile ID: " + profileId));

        var latestData = cfg.dsl().select(MICA_CLIENT_DATA.EXTERNAL_ID, MICA_CLIENT_DATA.PARSED_DATA)
                .on(MICA_CLIENT_DATA.EXTERNAL_ID).from(MICA_CLIENT_DATA)
                .orderBy(MICA_CLIENT_DATA.EXTERNAL_ID, MICA_CLIENT_DATA.IMPORTED_AT.desc())
                .fetch();

        if (latestData.isEmpty()) {
            throw ApiException.badRequest("No client data available");
        }

        var schema = deserialize(profile.getSchema(), ObjectSchemaNode.class);
        // TODO let users pick the property
        var endpointUriProperty = schema.properties().orElseGet(Map::of).entrySet().stream()
                .findFirst().orElseThrow(
                        () -> ApiException.internalError("No properties defined in the profile: " + profile.getName()));

        var validator = new Validator(objectMapper);

        for (Record2<String, JSONB> data : latestData) {
            var clientId = cfg.dsl()
                    .select(MICA_CLIENTS.ID).from(MICA_CLIENTS).where(MICA_CLIENTS.NAME.eq(data.value1()))
                    .fetchOptional(r -> r.get(MICA_CLIENTS.ID))
                    .orElseThrow(() -> ApiException.badRequest("Client not found: " + data.value1()));

            @SuppressWarnings("unchecked")
            var input = (Map<String, Object>) deserialize(data.value2(), Map.class);
            var result = validator.validateMap(schema, input);

            var endpointUri = result.getProperty(endpointUriProperty.getKey()).flatMap(ValidatedProperty::value);
            endpointUri.ifPresent(uri -> cfg.dsl().transaction(tx -> {
                tx.dsl().insertInto(MICA_CLIENT_ENDPOINTS)
                        .columns(MICA_CLIENT_ENDPOINTS.ID, MICA_CLIENT_ENDPOINTS.CLIENT_ID,
                                MICA_CLIENT_ENDPOINTS.ENDPOINT_URI, MICA_CLIENT_ENDPOINTS.LAST_KNOWN_STATUS)
                        .values(uuidGenerator.generate(), clientId, uri.textValue(), "UNKNOWN")
                        .execute();

                log.info("Inserted a new client endpoint, clientId={}, endpointUri={}", clientId, uri.textValue());
            }));
        }
    }

    private <T> T deserialize(JSONB json, Class<T> klass) {
        try {
            return objectMapper.readValue(json.data(), klass);
        } catch (IOException e) {
            throw ApiException
                    .internalError("Error while deserializing an existing profile's schema object: " + e.getMessage());
        }
    }
}
