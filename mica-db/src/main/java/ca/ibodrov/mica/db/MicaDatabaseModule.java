package ca.ibodrov.mica.db;

import com.codahale.metrics.MetricRegistry;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.walmartlabs.concord.db.DataSourceUtils;
import com.walmartlabs.concord.db.DatabaseChangeLogProvider;
import com.walmartlabs.concord.db.DatabaseConfiguration;
import com.walmartlabs.concord.db.MainDB;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Singleton;
import javax.sql.DataSource;

public class MicaDatabaseModule implements Module {

    @Override
    public void configure(Binder binder) {
    }

    @Provides
    @MicaDB
    @Singleton
    public DataSource dataSource(@MainDB DatabaseConfiguration cfg,
                                 MetricRegistry metricRegistry) {

        DataSource ds = DataSourceUtils.createDataSource(cfg, "mica", cfg.username(), cfg.password(), metricRegistry);
        DataSourceUtils.migrateDb(ds, new MicaDBChangeLogProvider(), cfg.changeLogParameters());
        return ds;
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

    @VisibleForTesting
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
