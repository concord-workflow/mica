package ca.ibodrov.mica.server;

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
