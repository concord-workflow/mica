package ca.ibodrov.mica.api.model;

public interface ViewLike {

    String name();

    Selector selector();

    Data data();

    interface Selector {

        String entityKind();
    }

    interface Data {

        String jsonPath();
    }
}
