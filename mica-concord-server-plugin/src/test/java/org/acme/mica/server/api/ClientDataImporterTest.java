package org.acme.mica.server.api;

import com.codahale.metrics.MetricRegistry;
import com.walmartlabs.concord.db.DatabaseConfiguration;
import org.acme.mica.db.MicaDatabaseModule;
import org.acme.mica.server.UuidGenerator;
import org.acme.mica.server.api.model.ClientDataDocument;
import org.acme.mica.server.api.model.ClientDataEntry;
import org.acme.mica.server.api.model.Document;
import org.acme.mica.server.data.ClientDataImporter;
import org.jooq.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ClientDataImporterTest {

    private PostgreSQLContainer<?> db;
    private DataSource dataSource;
    private Configuration jooqCfg;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setUp() {
        db = new PostgreSQLContainer<>("postgres:15-alpine");
        db.start();

        var dbCfg = new DatabaseConfigurationImpl(db.getJdbcUrl(), db.getUsername(), db.getPassword(), 3);
        var dbModule = new MicaDatabaseModule();
        dataSource = dbModule.dataSource(dbCfg, new MetricRegistry());
        jooqCfg = dbModule.jooqConfiguration(dataSource);
    }

    @AfterEach
    public void tearDown() throws Exception {
        Method m = dataSource.getClass().getMethod("close");
        m.invoke(dataSource);

        db.stop();
    }

    @Test
    public void testImport() {
        var document = new Document(Optional.of(ClientDataDocument.KIND),
                Map.of("clients",
                        List.of(
                                new ClientDataEntry("id1", Map.of()),
                                new ClientDataEntry("id2", Map.of()))));

        var importer = new ClientDataImporter(jooqCfg, new UuidGenerator());
        importer.importDocument(document);
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
