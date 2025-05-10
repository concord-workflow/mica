package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;

import java.net.URI;
import java.util.Optional;
import java.util.stream.Stream;

public interface EntityFetcher {

    default Optional<URI> defaultUri() {
        return Optional.empty();
    }

    boolean isSupported(FetchRequest request);

    Cursor fetch(FetchRequest request);

    record FetchRequest(Optional<URI> uri, Optional<String> kind) {

        public static FetchRequest ofUri(URI uri) {
            return new FetchRequest(Optional.of(uri), Optional.empty());
        }

        public static FetchRequest ofKind(String kind) {
            return new FetchRequest(Optional.empty(), Optional.of(kind));
        }
    }

    interface Cursor {

        Stream<EntityLike> stream();
    }
}
