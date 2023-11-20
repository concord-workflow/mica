package ca.ibodrov.mica.server.api.resources;

import ca.ibodrov.mica.server.api.ApiException;
import ca.ibodrov.mica.api.model.Document;
import ca.ibodrov.mica.server.data.DocumentImporter;
import ca.ibodrov.mica.server.data.InvalidDocumentException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.ws.rs.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

/**
 * Imports various YAML documents.
 */
@Tag(name = "Document")
@Path("/api/mica/v1/document")
public class DocumentResource implements Resource {

    private final Set<DocumentImporter> importers;
    private final ObjectMapper yamlMapper;

    @Inject
    public DocumentResource(Set<DocumentImporter> importers) {
        this.importers = requireNonNull(importers);
        if (importers.isEmpty()) {
            throw new RuntimeException("No DocumentImporter found");
        }
        this.yamlMapper = new ObjectMapper(new YAMLFactory().enable(JsonGenerator.Feature.IGNORE_UNKNOWN));
    }

    @POST
    @Path("import")
    @Consumes("*/*")
    @Produces(APPLICATION_JSON)
    @Operation(description = "Imports a YAML document", operationId = "importDocument")
    public ImportResponse importDocument(InputStream in) {
        Document doc;
        try {
            doc = yamlMapper.readValue(in, Document.class);
        } catch (IOException e) {
            throw new WebApplicationException("YAML parse error: " + e.getMessage(), BAD_REQUEST);
        }

        var handled = false;
        for (var importer : importers) {
            if (importer.canImport(doc)) {
                try {
                    importer.importDocument(doc);
                    handled = true;
                } catch (InvalidDocumentException e) {
                    throw ApiException.badRequest(e);
                }
            }
        }

        if (!handled) {
            throw ApiException.badRequest("No importer found for document kind: " + doc.getKind());
        }

        return new ImportResponse();
    }

    public record ImportResponse() {
    }
}
