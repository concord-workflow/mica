package org.acme.mica.server.api;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.acme.mica.db.DbUtils;
import org.acme.mica.db.MicaDB;
import org.jooq.Configuration;
import org.jooq.impl.DSL;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.ws.rs.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.acme.mica.db.jooq.Tables.CLIENT_DATA;

@Path("/api/mica/v1/clientData")
public class ClientDataResource implements Resource {

    private final ClientDataImporter importer;
    private final Configuration cfg;
    private final ObjectMapper yamlMapper;

    @Inject
    public ClientDataResource(ClientDataImporter importer, @MicaDB Configuration cfg) {
        this.importer = importer;
        this.cfg = cfg;
        this.yamlMapper = new ObjectMapper(new YAMLFactory().enable(Feature.IGNORE_UNKNOWN));
    }

    @POST
    @Path("import")
    @Consumes("*/*")
    @Produces(APPLICATION_JSON)
    public ImportResponse importClientDataDocument(InputStream in) {
        Document doc;
        try {
            doc = yamlMapper.readValue(in, Document.class);
        } catch (IOException e) {
            throw new WebApplicationException("YAML parse error: " + e.getMessage(), BAD_REQUEST);
        }
        var result = importer.importClientData(doc);
        return new ImportResponse(result.documentId());
    }

    @GET
    @Path("latest")
    @Produces(APPLICATION_JSON)
    public ClientData getLatestData(@QueryParam("externalId") @ValidClientName String externalId) {
        return DSL.using(cfg)
                .select(DbUtils.jsonbObject(CLIENT_DATA.PARSED_DATA, "properties")).from(CLIENT_DATA)
                .where(CLIENT_DATA.EXTERNAL_ID.eq(externalId))
                .orderBy(CLIENT_DATA.IMPORTED_AT.desc())
                .limit(1)
                .fetchOptional(record -> new ClientData(record.value1().data()))
                .orElseGet(() -> new ClientData("{}"));
    }

    public record ImportResponse(UUID documentId) {
    }

    public record ClientData(@JsonRawValue String properties) {
    }
}
