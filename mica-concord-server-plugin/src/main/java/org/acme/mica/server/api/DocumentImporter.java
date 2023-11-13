package org.acme.mica.server.api;

public interface DocumentImporter {

    boolean canImport(Document doc);

    void importDocument(Document doc);
}
