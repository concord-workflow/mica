package ca.ibodrov.mica.server.data.jsonStore;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.data.EntityFetcher;
import ca.ibodrov.mica.server.data.QueryParams;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreDataManager;

import javax.inject.Inject;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

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
    public boolean isSupported(FetchRequest request) {
        return request.uri()
                .map(uri -> URI_SCHEME.equals(uri.getScheme()))
                .orElse(false);
    }

    @Override
    public Cursor fetch(FetchRequest request) {
        var uri = request.uri().orElseThrow(() -> new StoreException("URI is required"));
        var query = Query.parse(uri);
        // TODO support for running a query (by name or by specifying the sql)
        // TODO pagination? streaming?
        var paths = dataManager.listItems(query.orgName, query.jsonStoreName, 0, -1, null);
        return () -> paths.stream()
                .flatMap(path -> {
                    var item = getItem(path, query);
                    var itemKind = getKind(path, item).orElse(query.defaultKind);
                    if (request.kind().isPresent() && !itemKind.equals(request.kind().get())) {
                        return Stream.empty();
                    }
                    var entity = toEntityLike(path, itemKind, item);
                    return Stream.of(entity);
                });
    }

    private JsonNode getItem(String path, Query query) {
        var s = dataManager.getItem(query.orgName, query.jsonStoreName, path);
        if (!(s instanceof String)) {
            throw new StoreException(
                    "Can't parse JSON store item %s, unexpected item type: %s".formatted(path, s.getClass()));
        }
        try {
            return objectMapper.readTree((String) s);
        } catch (JsonProcessingException e) {
            throw new StoreException("Can't parse JSON store item %s: %s".formatted(path, e.getMessage()));
        }
    }

    private EntityLike toEntityLike(String name, String kind, JsonNode item) {
        // TODO support different modes: treat items as entities, partial entities,
        // TODO entity bodies, etc.
        if (!item.isObject()) {
            throw new StoreException(
                    "Can't parse JSON store item %s, expected an object, got: %s".formatted(name, item.getNodeType()));
        }
        try {
            var data = objectMapper.convertValue(item, MAP_OF_JSON_NODES);
            return PartialEntity.create(name, kind, data);
        } catch (IllegalArgumentException e) {
            throw new StoreException("Error while parsing a JSON store item %s: %s".formatted(name, e.getMessage()), e);
        }
    }

    private static Optional<String> getKind(String path, JsonNode item) {

        return Optional.ofNullable(item.get("kind"))
                .map(kind -> {
                    if (!kind.isTextual()) {
                        throw new StoreException(
                                "Can't parse JSON store item %s, invalid kind value: %s".formatted(path, kind));
                    }
                    return kind.asText();
                });
    }

    private record Query(String orgName,
            String jsonStoreName,
            String defaultKind) {

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

            var jsonStoreName = pathElements[1];

            var queryParams = new QueryParams(uri.getQuery());
            var defaultKind = queryParams.getFirst("defaultKind").orElse(DEFAULT_ENTITY_KIND);

            return new Query(orgName, jsonStoreName, defaultKind);
        }
    }

    public static StoreException invalidUri(URI uri) {
        return new StoreException("Invalid URI, expected concord+jsonstore://{orgName}/{jsonStoreName}: " + uri);
    }
}
