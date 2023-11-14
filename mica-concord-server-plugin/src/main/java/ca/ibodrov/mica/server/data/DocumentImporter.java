package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.server.api.model.Document;

public interface DocumentImporter {

    boolean canImport(Document doc);

    void importDocument(Document doc);
}
