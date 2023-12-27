package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;

import java.net.URI;
import java.util.List;

public interface EntityFetcher {

    // TODO return a custom cursor type to allow better resource management (e.g.
    // git locks)
    List<EntityLike> getAllByKind(URI uri, String kind, int limit);
}
