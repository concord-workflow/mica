package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;

import java.net.URI;
import java.util.stream.Stream;

public interface EntityFetcher {

    boolean isSupported(URI uri);

    /**
     * TODO re-implement as Cursor query(URI uri, Query query)
     */
    Cursor getAllByKind(URI uri, String kind, int limit);

    interface Cursor {

        Stream<EntityLike> stream();
    }
}
