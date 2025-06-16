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

import ca.ibodrov.mica.db.MicaDatabaseModule.MicaDBChangeLogProvider;
import com.codahale.metrics.MetricRegistry;
import com.walmartlabs.concord.db.DataSourceUtils;
import com.walmartlabs.concord.db.DatabaseConfiguration;
import com.walmartlabs.concord.db.MainDB;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.sql.DataSource;

public class MicaDataSourceProvider implements Provider<DataSource> {

    private final DatabaseConfiguration cfg;
    private final MetricRegistry metricRegistry;

    @Inject
    public MicaDataSourceProvider(@MainDB DatabaseConfiguration cfg, MetricRegistry metricRegistry) {
        this.cfg = cfg;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public DataSource get() {
        var changeLog = new MicaDBChangeLogProvider();
        DataSourceUtils.migrateDb(cfg, changeLog);
        return DataSourceUtils.createDataSource(cfg, "mica", cfg.username(), cfg.password(), metricRegistry);
    }
}
