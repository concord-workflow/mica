package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;

import java.net.URI;
import java.util.List;

public interface EntityFetcher {

    boolean isSupported(URI uri);

    // TODO custom cursor type
    List<EntityLike> getAllByKind(URI uri, String kind, int limit);
}
