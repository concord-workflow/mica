package ca.ibodrov.mica.api.model;

import ca.ibodrov.mica.schema.ObjectSchemaNode;

import java.util.Map;

public interface ViewLike {

    String name();

    Selector selector();

    Data data();

    Map<String, ObjectSchemaNode> parameters();

    interface Selector {

        String entityKind();
    }

    interface Data {

        String jsonPath();

        boolean flatten();
    }
}
