package ca.ibodrov.mica.server.api.resources;

import ca.ibodrov.mica.api.model.Client;
import ca.ibodrov.mica.api.model.ClientList;
import ca.ibodrov.mica.db.MicaDB;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.validator.constraints.Length;
import org.jooq.Configuration;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.ws.rs.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_CLIENTS;
import static ca.ibodrov.mica.db.jooq.Tables.MICA_CLIENT_DATA;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.jooq.JSONB.jsonb;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.val;

@Path("/api/mica/v1/client")
public class ClientResource implements Resource {

    private final Configuration cfg;
    private final ObjectMapper objectMapper;

    @Inject
    public ClientResource(@MicaDB Configuration cfg, ObjectMapper objectMapper) {
        this.cfg = cfg;
        this.objectMapper = objectMapper;
    }

    @GET
    @Produces(APPLICATION_JSON)
    public ClientList listClients(@QueryParam("search") @Length(max = 128) String search,
                                  @QueryParam("props") Set<String> props) {

        var searchCondition = Optional.ofNullable(search)
                .map(MICA_CLIENTS.NAME::containsIgnoreCase)
                .orElseGet(DSL::noCondition);

        var latestData = props.isEmpty() ? val(jsonb("{}")).as("latest_data")
                : select(MICA_CLIENT_DATA.PARSED_DATA).from(MICA_CLIENT_DATA)
                        .where(MICA_CLIENT_DATA.EXTERNAL_ID.eq(MICA_CLIENTS.NAME))
                        .orderBy(MICA_CLIENT_DATA.IMPORTED_AT.desc())
                        .limit(1)
                        .asField("latest_data");

        var data = cfg.dsl()
                .select(MICA_CLIENTS.ID, MICA_CLIENTS.NAME, latestData)
                .from(MICA_CLIENTS)
                .where(searchCondition)
                .fetch(r -> new Client(r.get(MICA_CLIENTS.ID), r.get(MICA_CLIENTS.NAME),
                        parseAndFilterProperties(r.get("latest_data", JSONB.class), props)));

        return new ClientList(data);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAndFilterProperties(JSONB json, Set<String> propsToPick) {
        if (json == null || propsToPick == null || propsToPick.isEmpty()) {
            return Map.of();
        }

        try {
            var data = (Map<String, Object>) objectMapper.readValue(json.data(), Map.class);

            var result = new HashMap<String, Object>();
            for (var prop : propsToPick) {
                if (!data.containsKey(prop)) {
                    continue;
                }

                var value = data.get(prop);
                if (value != null) {
                    result.put(prop, value);
                }
            }
            return result;
        } catch (IOException e) {
            throw new WebApplicationException(INTERNAL_SERVER_ERROR);
        }
    }
}
