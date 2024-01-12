package ca.ibodrov.mica.db;

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
        DataSource ds = DataSourceUtils.createDataSource(cfg, "mica", cfg.username(), cfg.password(), metricRegistry);
        DataSourceUtils.migrateDb(ds, new MicaDBChangeLogProvider(), cfg.changeLogParameters());
        return ds;
    }
}
