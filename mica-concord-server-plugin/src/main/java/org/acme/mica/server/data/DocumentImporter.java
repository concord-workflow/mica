package org.acme.mica.server.data;

import org.acme.mica.server.api.model.Document;

public interface DocumentImporter {

    boolean canImport(Document doc);

    void importDocument(Document doc);
}
