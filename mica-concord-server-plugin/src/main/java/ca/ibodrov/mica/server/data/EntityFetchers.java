package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.server.data.EntityFetcher.FetchRequest;
import ca.ibodrov.mica.server.exceptions.ApiException;
import ca.ibodrov.mica.server.exceptions.StoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class EntityFetchers {

    private static final Logger log = LoggerFactory.getLogger(EntityFetchers.class);

    private final Set<EntityFetcher> fetchers;

    @Inject
    public EntityFetchers(Set<EntityFetcher> fetchers) {
        this.fetchers = requireNonNull(fetchers);
    }

    /**
     * Fetches all entities of the given kind from all stores.
     */
    public Stream<EntityWithSource> fetchAll(String entityKind) {
        var request = FetchRequest.ofKind(entityKind);
        return fetchers.stream()
                .filter(f -> f.isSupported(request))
                .flatMap(f -> f.fetch(request).stream()
                        .map(entity -> new EntityWithSource(entity, f.defaultUri())));
    }

    /**
     * Fetches entities of the given kind from the given store represented by the
     * URI.
     */
    public Stream<EntityLike> fetch(URI uri, String entityKind) {
        var request = new FetchRequest(Optional.of(uri), Optional.of(entityKind));

        var fetcher = fetchers.stream()
                .filter(f -> f.isSupported(request))
                .findAny()
                .orElseThrow(() -> ApiException.badRequest("Unsupported URI in \"includes\": " + uri));

        try {
            return fetcher.fetch(request).stream();
        } catch (StoreException e) {
            log.warn("Error while fetching {} entities: {}", uri.getScheme(), e.getMessage());
            throw ApiException.internalError(e.getMessage());
        }
    }

    public record EntityWithSource(EntityLike entity, Optional<URI> source) {
    }
}
