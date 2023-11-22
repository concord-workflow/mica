package ca.ibodrov.mica.db;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.walmartlabs.concord.db.DataSourceUtils;
import com.walmartlabs.concord.db.DatabaseChangeLogProvider;
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
