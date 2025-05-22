package ca.ibodrov.mica.server;

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

import ca.ibodrov.mica.db.MicaDataSourceProvider;
import ca.ibodrov.mica.db.MicaDatabaseModule;
import com.codahale.metrics.MetricRegistry;
import com.walmartlabs.concord.db.DatabaseConfiguration;
import org.jooq.Configuration;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.time.Duration;

/**
 * A wrapper for PostgreSQLContainer for use in unit tests.
 */
public class TestDatabase implements AutoCloseable {

    private PostgreSQLContainer<?> container;
    private DataSource dataSource;
    private Configuration jooqConfiguration;

    public void start() {
        container = new PostgreSQLContainer<>("postgres:15-alpine");
        container.start();

        var dbCfg = new DatabaseConfigurationImpl(container.getJdbcUrl(), container.getUsername(),
                container.getPassword(), 3);
        var dbModule = new MicaDatabaseModule();
        dataSource = new MicaDataSourceProvider(dbCfg, new MetricRegistry()).get();
        jooqConfiguration = dbModule.jooqConfiguration(dataSource);
    }

    public Configuration getJooqConfiguration() {
        return jooqConfiguration;
    }

    @Override
    public void close() throws Exception {
        Method m = dataSource.getClass().getMethod("close");
        m.invoke(dataSource);

        container.stop();
    }

    private record DatabaseConfigurationImpl(String url,
            String username,
            String password,
            int maxPoolSize)
            implements DatabaseConfiguration {

        @Override
        public Duration maxLifetime() {
            return Duration.ofSeconds(30);
        }
    }
}
