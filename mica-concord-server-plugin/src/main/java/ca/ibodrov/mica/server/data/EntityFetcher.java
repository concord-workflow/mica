package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;

import java.net.URI;
import java.util.List;

public interface EntityFetcher {

    // TODO a method to determine if the fetcher can handle the given URI
    // to pre-select the fetchers and run them in parallel if needed

    // TODO return a custom cursor type to allow better resource management (e.g.
    // git locks)
    List<EntityLike> getAllByKind(URI uri, String kind, int limit);
}
