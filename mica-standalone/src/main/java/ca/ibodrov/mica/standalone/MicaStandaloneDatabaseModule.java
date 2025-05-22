package ca.ibodrov.mica.standalone;

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

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.walmartlabs.concord.db.*;
import org.jooq.Configuration;

import javax.inject.Singleton;
import javax.sql.DataSource;

/**
 * Configures standard Concord data source for the standalone version of Mica.
 */
public class MicaStandaloneDatabaseModule implements Module {

    @Override
    public void configure(Binder binder) {
    }

    @Provides
    @MainDB
    @Singleton
    public DataSource mainDataSource(@MainDB DatabaseConfiguration cfg,
                                     MetricRegistry metricRegistry,
                                     MainDBChangeLogProvider mainDbChangeLog) {
        DataSource ds = DataSourceUtils.createDataSource(cfg, "app", cfg.username(), cfg.password(), metricRegistry);
        DataSourceUtils.migrateDb(ds, mainDbChangeLog, cfg.changeLogParameters());
        return ds;
    }

    @Provides
    @MainDB
    @Singleton
    public Configuration appJooqConfiguration(@MainDB DataSource ds) {
        return DataSourceUtils.createJooqConfiguration(ds);
    }

    @Provides
    @JsonStorageDB
    @Singleton
    public DataSource inventoryDataSource(@JsonStorageDB DatabaseConfiguration cfg, MetricRegistry metricRegistry) {
        return DataSourceUtils.createDataSource(cfg, "inventory", cfg.username(), cfg.password(), metricRegistry);
    }

    @Provides
    @JsonStorageDB
    @Singleton
    public Configuration inventoryJooqConfiguration(@JsonStorageDB DataSource ds) {
        return DataSourceUtils.createJooqConfiguration(ds);
    }
}
