package ca.ibodrov.mica.server.ui;

import ca.ibodrov.mica.api.model.EntityId;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record4;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.*;
import java.time.Instant;
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
    @Path("canBeDeleted")
    public CanBeDeletedResponse canBeDeleted(@NotEmpty @QueryParam("entityId") EntityId entityId) {
        var record = dsl.select(MICA_ENTITIES.NAME, MICA_ENTITIES.DELETED_AT)
                .from(MICA_ENTITIES)
                .where(MICA_ENTITIES.ID.eq(entityId.id()))
                .fetchOptional()
                .orElseThrow(() -> ApiException.notFound("Entity ID not found: " + entityId.toExternalForm()));

        if (record.component1().startsWith("/mica/")) {
            // do not delete "system" entities
            return new CanBeDeletedResponse(false, Optional.of("System entities cannot be deleted."));
        }

        // check if already "deleted"
        if (record.component2() == null) {
            return new CanBeDeletedResponse(false, Optional.of("Already deleted"));
        }

        return new CanBeDeletedResponse(true, Optional.empty());
    }

    @GET
    public ListResponse list(@NotEmpty @QueryParam("path") String path,
                             @Nullable @QueryParam("entityKind") String entityKind,
                             @Nullable @QueryParam("search") String search,
                             @QueryParam("deleted") @DefaultValue("false") boolean deleted) {

        assert path != null;

        var namePrefix = path.endsWith("/") ? path : path + "/";

        var entityKindCondition = Optional.ofNullable(nonBlank(entityKind))
                .map(MICA_ENTITIES.KIND::eq).orElse(noCondition());

        var applySearch = search != null && !search.isBlank();
        var searchCondition = Optional.ofNullable(nonBlank(search))
                .map(s -> (Condition) MICA_ENTITIES.NAME.likeIgnoreCase("%" + s + "%")).orElse(noCondition());

        var deletedCondition = deleted ? MICA_ENTITIES.DELETED_AT.isNotNull() : MICA_ENTITIES.DELETED_AT.isNull();

        var result = new HashMap<String, List<Entry>>();
        dsl.select(MICA_ENTITIES.ID, MICA_ENTITIES.NAME, MICA_ENTITIES.KIND, MICA_ENTITIES.DELETED_AT)
                .from(MICA_ENTITIES)
                .where(MICA_ENTITIES.NAME.like(namePrefix + "%")
                        .and(entityKindCondition)
                        .and(searchCondition)
                        .and(deletedCondition))
                .limit(applySearch ? SEARCH_LIMIT : null)
                .forEach(record -> {
                    if (applySearch) {
                        var entry = asSearchResult(record);
                        var entries = result.computeIfAbsent(entry.getKey(), key -> new ArrayList<>());
                        if (!entries.contains(entry.getValue())) {
                            entries.add(entry.getValue());
                        }
                    } else {
                        var entry = asTreeEntry(record, namePrefix);
                        var entries = result.computeIfAbsent(entry.getKey(), key -> new ArrayList<>());
                        if (!entries.contains(entry.getValue())) {
                            entries.add(entry.getValue());
                        }
                    }
                });

        var data = result.values().stream()
                .flatMap(Collection::stream)
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

    private static Map.Entry<String, Entry> asTreeEntry(Record4<UUID, String, String, Instant> record,
                                                        String namePrefix) {
        var relativePath = record.value2().substring(namePrefix.length());
        if (relativePath.contains("/")) {
            var name = relativePath.split("/")[0];
            return Map.entry(name, new Entry(Type.FOLDER, Optional.empty(), name, Optional.empty(), Optional.empty()));
        } else {
            return Map.entry(relativePath,
                    new Entry(Type.FILE,
                            Optional.of(new EntityId(record.value1())),
                            relativePath,
                            Optional.of(record.value3()),
                            Optional.ofNullable(record.value4())));
        }
    }

    private static Map.Entry<String, Entry> asSearchResult(Record4<UUID, String, String, Instant> record) {
        return Map.entry(record.value2(),
                new Entry(Type.FILE,
                        Optional.of(new EntityId(record.value1())),
                        record.value2(),
                        Optional.of(record.value3()),
                        Optional.ofNullable(record.value4())));
    }

    public record CanBeDeletedResponse(boolean canBeDeleted, Optional<String> whyNot) {
    }

    public enum Type {
        FOLDER,
        FILE
    }

    @JsonInclude(Include.NON_ABSENT)
    public record Entry(Type type, Optional<EntityId> entityId, String name, Optional<String> entityKind,
            Optional<Instant> deletedAt) {
    }

    public record ListResponse(List<Entry> data) {
    }
}
