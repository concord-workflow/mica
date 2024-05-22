package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;

import java.net.URI;
import java.util.Optional;
import java.util.stream.Stream;

public interface EntityFetchers {

    Stream<EntityWithSource> fetchAll(String entityKind);

    Stream<EntityLike> fetch(URI uri, String entityKind);

    record EntityWithSource(EntityLike entity, Optional<URI> source) {
    }
}
