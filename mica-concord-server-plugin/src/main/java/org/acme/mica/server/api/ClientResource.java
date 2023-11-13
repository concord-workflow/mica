package org.acme.mica.server.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.acme.mica.db.DbUtils;
import org.acme.mica.db.MicaDB;
import org.hibernate.validator.constraints.Length;
import org.jooq.Configuration;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.acme.mica.db.jooq.Tables.CLIENTS;
import static org.acme.mica.db.jooq.Tables.CLIENT_DATA;
import static org.jooq.JSONB.jsonb;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.val;

@Path("/api/mica/v1/client")
public class ClientResource implements Resource {

    private final Configuration cfg;
    private final ObjectMapper objectMapper;

    @Inject
    public ClientResource(@MicaDB Configuration cfg) {
        this.cfg = cfg;
        this.objectMapper = new ObjectMapper();
    }

    @GET
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public ClientList listClients(@QueryParam("search") @Length(max = 128) String search,
                                  @QueryParam("props") Set<String> props) {

        var searchCondition = Optional.ofNullable(search)
                .map(CLIENTS.NAME::containsIgnoreCase)
                .orElseGet(DSL::noCondition);

        var latestData = props.isEmpty() ? val(jsonb("{}")).as("latest_data")
                : select(DbUtils.jsonbObject(CLIENT_DATA.PARSED_DATA, "properties")).from(CLIENT_DATA)
                        .where(CLIENT_DATA.EXTERNAL_ID.eq(CLIENTS.NAME))
                        .orderBy(CLIENT_DATA.IMPORTED_AT.desc())
                        .limit(1)
                        .asField("latest_data");

        var data = DSL.using(cfg)
                .select(CLIENTS.ID, CLIENTS.NAME, latestData)
                .from(CLIENTS)
                .where(searchCondition)
                .fetch()
                .map(r -> new Client(r.get(CLIENTS.ID), r.get(CLIENTS.NAME),
                        parseAndFilterProperties(r.get("latest_data", JSONB.class), props)));

        return new ClientList(data);
    }

    public record ClientList(List<Client> data) {
    }

    public record Client(@NotEmpty UUID id,
            @ValidClientName String name,
            Map<String, Object> properties) {
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAndFilterProperties(JSONB json, Set<String> props) {
        if (json == null || props.isEmpty()) {
            return Map.of();
        }

        try {
            var data = (Map<String, Object>) objectMapper.readValue(json.data(), Map.class);
            return data.entrySet().stream()
                    .filter(kv -> props.contains(kv.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        } catch (IOException e) {
            throw new WebApplicationException(INTERNAL_SERVER_ERROR);
        }
    }
}
