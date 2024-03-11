package ca.ibodrov.mica.server.ui;

import ca.ibodrov.mica.api.model.EntityId;
import ca.ibodrov.mica.db.MicaDB;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record3;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.util.*;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_ENTITIES;
import static ca.ibodrov.mica.server.api.ApiUtils.nonBlank;
import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.jooq.impl.DSL.noCondition;

@Path("/api/mica/ui/entityList")
@Produces(APPLICATION_JSON)
public class EntityListResource implements Resource {

    private static final int SEARCH_LIMIT = 100;

    private final DSLContext dsl;

    @Inject
    public EntityListResource(@MicaDB DSLContext dsl) {
        this.dsl = requireNonNull(dsl);
    }

    @GET
    public ListResponse list(@NotEmpty @QueryParam("path") String path,
                             @Nullable @QueryParam("entityKind") String entityKind,
                             @Nullable @QueryParam("search") String search) {

        assert path != null;

        var namePrefix = path.endsWith("/") ? path : path + "/";

        var entityKindCondition = Optional.ofNullable(nonBlank(entityKind))
                .map(MICA_ENTITIES.KIND::eq).orElse(noCondition());

        var applySearch = search != null && !search.isBlank();
        var searchCondition = Optional.ofNullable(nonBlank(search))
                .map(s -> (Condition) MICA_ENTITIES.NAME.likeIgnoreCase("%" + s + "%")).orElse(noCondition());

        var result = new HashMap<String, Entry>();
        dsl.select(MICA_ENTITIES.ID, MICA_ENTITIES.NAME, MICA_ENTITIES.KIND).from(MICA_ENTITIES)
                .where(MICA_ENTITIES.NAME.like(namePrefix + "%")
                        .and(entityKindCondition)
                        .and(searchCondition))
                .limit(applySearch ? SEARCH_LIMIT : null)
                .forEach(record -> {
                    if (applySearch) {
                        var entry = asSearchResult(record);
                        result.put(entry.getKey(), entry.getValue());
                    } else {
                        var entry = asTreeEntry(record, namePrefix);
                        result.put(entry.getKey(), entry.getValue());
                    }
                });

        var data = result.entrySet().stream()
                .map(Map.Entry::getValue)
                .sorted(EntityListResource::compare)
                .toList();

        return new ListResponse(data);
    }

    private static int compare(Entry e1, Entry e2) {
        // folders first, then files

        if (e1.type() == Type.FOLDER && e2.type() == Type.FILE) {
            return -1;
        }
        if (e1.type() == Type.FILE && e2.type() == Type.FOLDER) {
            return 1;
        }

        // sort by name
        return e1.name().compareTo(e2.name());
    }

    private static Map.Entry<String, Entry> asTreeEntry(Record3<UUID, String, String> record, String namePrefix) {
        var relativePath = record.value2().substring(namePrefix.length());
        if (relativePath.contains("/")) {
            var name = relativePath.split("/")[0];
            return Map.entry(name, new Entry(Type.FOLDER, Optional.empty(), name, Optional.empty()));
        } else {
            return Map.entry(relativePath,
                    new Entry(Type.FILE,
                            Optional.of(new EntityId(record.value1())),
                            relativePath,
                            Optional.of(record.value3())));
        }
    }

    private static Map.Entry<String, Entry> asSearchResult(Record3<UUID, String, String> record) {
        return Map.entry(record.value2(),
                new Entry(Type.FILE,
                        Optional.of(new EntityId(record.value1())),
                        record.value2(),
                        Optional.of(record.value3())));
    }

    public enum Type {
        FOLDER,
        FILE
    }

    @JsonInclude(Include.NON_ABSENT)
    public record Entry(Type type, Optional<EntityId> entityId, String name, Optional<String> entityKind) {
    }

    public record ListResponse(List<Entry> data) {
    }
}
