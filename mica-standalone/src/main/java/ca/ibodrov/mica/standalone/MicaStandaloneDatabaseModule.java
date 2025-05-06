package ca.ibodrov.mica.standalone;

import ca.ibodrov.mica.db.MicaDatabaseModule;
import com.codahale.metrics.MetricRegistry;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.walmartlabs.concord.db.*;
import org.jooq.Configuration;

import javax.inject.Singleton;
import javax.sql.DataSource;

/**
 * Installs {@link MicaDatabaseModule} and configures standard Concord data
 * sources.
 */
public class MicaStandaloneDatabaseModule implements Module {

    @Override
    public void configure(Binder binder) {
        binder.install(new MicaDatabaseModule());
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
