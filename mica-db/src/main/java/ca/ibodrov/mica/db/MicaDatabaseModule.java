package ca.ibodrov.mica.db;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.walmartlabs.concord.db.*;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Singleton;
import javax.sql.DataSource;

public class MicaDatabaseModule implements Module {

    @Override
    public void configure(Binder binder) {
        // bind as eager singleton to run migrations early
        binder.bind(DataSource.class).annotatedWith(MicaDB.class).toProvider(MicaDataSourceProvider.class)
                .asEagerSingleton();
    }

    @Provides
    @MainDB
    @Singleton
    public DataSource mainDataSource(@MainDB DatabaseConfiguration cfg,
                                     MetricRegistry metricRegistry,
                                     MainDBChangeLogProvider mainDbChangeLog,
                                     MicaDBChangeLogProvider micaDbChangeLog) {

        DataSource ds = DataSourceUtils.createDataSource(cfg, "app", cfg.username(), cfg.password(), metricRegistry);
        DataSourceUtils.migrateDb(ds, mainDbChangeLog, cfg.changeLogParameters());
        DataSourceUtils.migrateDb(ds, micaDbChangeLog, cfg.changeLogParameters());

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

    @Provides
    @MicaDB
    @Singleton
    public Configuration micaJooqConfiguration(@MicaDB DataSource ds) {
        return DataSourceUtils.createJooqConfiguration(ds);
    }

    @Provides
    @MicaDB
    public DSLContext micaDslContext(@MicaDB Configuration cfg) {
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
