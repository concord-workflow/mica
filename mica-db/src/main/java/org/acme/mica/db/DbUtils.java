package org.acme.mica.db;

import org.jooq.Field;
import org.jooq.JSONB;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;

public final class DbUtils {

    public static Field<JSONB> jsonbObject(Field<JSONB> field, String name) {
        return field("{0}::jsonb->>{1}", JSONB.class, field, inline(name));
    }

    private DbUtils() {
    }
}
