package ca.ibodrov.mica.server;

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

    private PostgreSQLContainer<?> db;
    private DataSource dataSource;
    private Configuration jooqConfiguration;

    public void start() {
        db = new PostgreSQLContainer<>("postgres:15-alpine");
        db.start();

        var dbCfg = new DatabaseConfigurationImpl(db.getJdbcUrl(), db.getUsername(), db.getPassword(), 3);
        var dbModule = new MicaDatabaseModule();
        dataSource = dbModule.dataSource(dbCfg, new MetricRegistry());
        jooqConfiguration = dbModule.jooqConfiguration(dataSource);
    }

    public Configuration getJooqConfiguration() {
        return jooqConfiguration;
    }

    @Override
    public void close() throws Exception {
        Method m = dataSource.getClass().getMethod("close");
        m.invoke(dataSource);

        db.stop();
    }

    private static final class DatabaseConfigurationImpl implements DatabaseConfiguration {

        private final String url;
        private final String username;
        private final String password;
        private final int maxPoolSize;

        private DatabaseConfigurationImpl(String url, String username, String password, int maxPoolSize) {
            this.url = url;
            this.username = username;
            this.password = password;
            this.maxPoolSize = maxPoolSize;
        }

        @Override
        public String url() {
            return url;
        }

        @Override
        public String username() {
            return username;
        }

        @Override
        public String password() {
            return password;
        }

        @Override
        public int maxPoolSize() {
            return maxPoolSize;
        }

        @Override
        public Duration maxLifetime() {
            return Duration.ofSeconds(30);
        }
    }
}
