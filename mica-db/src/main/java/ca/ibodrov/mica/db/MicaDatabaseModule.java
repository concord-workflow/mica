package ca.ibodrov.mica.db;

/*-
 * ~~~~~~
 * Mica
 * ------
 * Copyright (C) 2023 - 2025 Mica Authors
 * ------
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ======
 */

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.walmartlabs.concord.db.DataSourceUtils;
import com.walmartlabs.concord.db.DatabaseChangeLogProvider;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Singleton;
import javax.sql.DataSource;

/**
 * Configures Mica-specific data source and schema.
 */
public class MicaDatabaseModule implements Module {

    @Override
    public void configure(Binder binder) {
        // bind as eager singleton to run migrations early
        binder.bind(DataSource.class).annotatedWith(MicaDB.class).toProvider(MicaDataSourceProvider.class)
                .asEagerSingleton();
    }

    @Provides
    @MicaDB
    @Singleton
    public Configuration jooqConfiguration(@MicaDB DataSource ds) {
        return DataSourceUtils.createJooqConfiguration(ds);
    }

    @Provides
    @MicaDB
    public DSLContext dslContext(@MicaDB Configuration cfg) {
        return cfg.dsl();
    }

    public static class MicaDBChangeLogProvider implements DatabaseChangeLogProvider {

        @Override
        public String getChangeLogPath() {
            return "ca/ibodrov/mica/db/liquibase.xml";
        }

        @Override
        public String toString() {
            return "mica-db";
        }
    }
}
