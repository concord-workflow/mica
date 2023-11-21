package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.Document;
import ca.ibodrov.mica.server.exceptions.InvalidDocumentException;

public interface DocumentImporter {

    boolean canImport(Document doc);

    void importDocument(Document doc) throws InvalidDocumentException;
}
