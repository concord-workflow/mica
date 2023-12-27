package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;

import java.net.URI;
import java.util.stream.Stream;

public interface EntityFetcher {

    Stream<EntityLike> getAllByKind(URI uri, String kind, int limit);
}
