package ca.ibodrov.mica.server;

import ca.ibodrov.mica.db.MicaDatabaseModule;
import ca.ibodrov.mica.server.api.*;
import ca.ibodrov.mica.server.data.*;
import ca.ibodrov.mica.server.exceptions.DataAccessExceptionMapper;
import ca.ibodrov.mica.server.exceptions.StoreExceptionExceptionMapper;
import ca.ibodrov.mica.server.exceptions.ViewProcessorExceptionMapper;
import ca.ibodrov.mica.server.ui.EditorSchemaResource;
import ca.ibodrov.mica.server.ui.OidcResource;
import ca.ibodrov.mica.server.ui.SwaggerServlet;
import ca.ibodrov.mica.server.ui.WhoamiResource;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.typesafe.config.Config;
import com.walmartlabs.concord.server.boot.FilterChainConfigurator;
import com.walmartlabs.concord.server.sdk.rest.Component;
import com.walmartlabs.ollie.config.ConfigurationProcessor;
import com.walmartlabs.ollie.config.Environment;
import com.walmartlabs.ollie.config.EnvironmentSelector;
import com.walmartlabs.ollie.config.OllieConfigurationModule;

import javax.inject.Named;
import javax.servlet.http.HttpServlet;
import javax.ws.rs.ext.ExceptionMapper;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.walmartlabs.concord.server.Utils.bindJaxRsResource;

/**
 * Mica's Guice module.
 * <p/>
 * Several things are assumed to be provided by the environment:
 * <ul>
 * <li>{@link com.fasterxml.jackson.databind.ObjectMapper}</li>
 * <li>{@link com.walmartlabs.concord.db.DatabaseConfiguration} annotated as
 * {@link com.walmartlabs.concord.db.MainDB}</li>
 * <li>{@link com.codahale.metrics.MetricRegistry}</li>
 * </ul>
 */
@Named
public class MicaModule implements Module {

    private Config config;

    public MicaModule() {
        this(loadDefaultConfig());
    }

    public MicaModule(Config config) {
        this.config = config;
    }

    @Override
    public void configure(Binder binder) {
        // DB

        binder.install(new MicaDatabaseModule());

        // config

        if (config == null) {
            throw new RuntimeException("The config property must be injected before calling configure()");
        }
        binder.install(new OllieConfigurationModule("ca.ibodrov.mica.server", config));

        // servlets

        newSetBinder(binder, HttpServlet.class).addBinding().to(SwaggerServlet.class).in(SINGLETON);

        // filter chains

        newSetBinder(binder, FilterChainConfigurator.class).addBinding().to(MicaFilterChainConfigurator.class);

        // exception mappers

        bindExceptionMapper(binder, DataAccessExceptionMapper.class);
        bindExceptionMapper(binder, StoreExceptionExceptionMapper.class);
        bindExceptionMapper(binder, ViewProcessorExceptionMapper.class);

        // jax-rs resources

        bindJaxRsResource(binder, BatchOperationResource.class);
        bindJaxRsResource(binder, EditorSchemaResource.class);
        bindJaxRsResource(binder, EntityResource.class);
        bindJaxRsResource(binder, EntityUploadResource.class);
        bindJaxRsResource(binder, OidcResource.class);
        bindJaxRsResource(binder, SystemResource.class);
        bindJaxRsResource(binder, ViewResource.class);
        bindJaxRsResource(binder, WhoamiResource.class);

        // other beans

        binder.bind(BuiltinSchemas.class).in(SINGLETON);
        binder.bind(EntityKindStore.class).in(SINGLETON);
        binder.bind(EntityStore.class).in(SINGLETON);
        binder.bind(UuidGenerator.class).in(SINGLETON);

        binder.bind(InitialDataLoader.class).asEagerSingleton();

        newSetBinder(binder, EntityFetcher.class).addBinding().to(InternalEntityFetcher.class);
        newSetBinder(binder, EntityFetcher.class).addBinding().to(ConcordGitEntityFetcher.class);
    }

    private static Config loadDefaultConfig() {
        // TODO avoid re-reading the config
        Environment env = new EnvironmentSelector().select();
        return new ConfigurationProcessor("concord-server", env, null, null).process();
    }

    public static <K extends ExceptionMapper<?> & Component> void bindExceptionMapper(Binder binder, Class<K> klass) {
        binder.bind(klass).in(SINGLETON);
        newSetBinder(binder, Component.class).addBinding().to(klass);
        newSetBinder(binder, ExceptionMapper.class).addBinding().to(klass);
    }
}
