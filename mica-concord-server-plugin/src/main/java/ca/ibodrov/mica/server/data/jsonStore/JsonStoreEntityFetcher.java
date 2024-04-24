package ca.ibodrov.mica.server.data.jsonStore;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.data.EntityFetcher;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreDataManager;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class JsonStoreEntityFetcher implements EntityFetcher {

    private static final String URI_SCHEME = "concord+jsonstore";
    private static final String DEFAULT_ENTITY_KIND = "/concord/json-store/item/v1";
    private static final TypeReference<Map<String, JsonNode>> MAP_OF_JSON_NODES = new TypeReference<>() {
    };

    private final JsonStoreDataManager dataManager;
    private final ObjectMapper objectMapper;

    @Inject
    public JsonStoreEntityFetcher(JsonStoreDataManager dataManager,
                                  ObjectMapper objectMapper) {
        this.dataManager = requireNonNull(dataManager);
        this.objectMapper = requireNonNull(objectMapper);
    }

    @Override
    public boolean isSupported(URI uri) {
        return URI_SCHEME.equals(uri.getScheme());
    }

    @Override
    public Cursor getAllByKind(URI uri, String kind, int limit) {
        // TODO filter by kind?
        var query = Query.parse(uri);
        // TODO support for running a query (by name or by specifying the sql)
        // TODO pagination? streaming?
        var paths = dataManager.listItems(query.orgName, query.jsonStoreName, 0, limit, null);
        return () -> paths.stream()
                .map(path -> {
                    var item = dataManager.getItem(query.orgName, query.jsonStoreName, path);
                    return toEntityLike(path, item);
                });
    }

    private EntityLike toEntityLike(String name, Object item) {
        // TODO support different modes: treat items as entities, partial entities,
        // TODO entity bodies, etc.
        if (!(item instanceof String)) {
            throw new StoreException("Unexpected item type: " + item.getClass());
        }
        try {
            var data = objectMapper.readValue((String) item, MAP_OF_JSON_NODES);
            return PartialEntity.create(name, DEFAULT_ENTITY_KIND, data);
        } catch (IOException e) {
            throw new StoreException("Error while parsing a JSON store item %s: %s".formatted(name, e.getMessage()), e);
        }
    }

    private record Query(String orgName, String jsonStoreName) {

        static Query parse(URI uri) {
            if (!URI_SCHEME.equals(uri.getScheme())) {
                throw new StoreException("Unsupported URI scheme: " + uri.getScheme());
            }

            var orgName = uri.getAuthority();
            if (orgName == null || orgName.isBlank()) {
                throw invalidUri(uri);
            }

            if (uri.getPath() == null) {
                throw invalidUri(uri);
            }

            var pathElements = uri.getPath().split("/");
            if (pathElements.length != 2) {
                throw invalidUri(uri);
            }

            return new Query(orgName, pathElements[1]);
        }
    }

    public static StoreException invalidUri(URI uri) {
        return new StoreException("Invalid URI, expected concord+jsonstore://{orgName}/{jsonStoreName}: " + uri);
    }
}
