package ca.ibodrov.mica.server;

import ca.ibodrov.mica.db.MicaDatabaseModule;
import ca.ibodrov.mica.server.api.resources.*;
import ca.ibodrov.mica.server.data.ClientDataImporter;
import ca.ibodrov.mica.server.data.DocumentImporter;
import ca.ibodrov.mica.server.data.ProfileImporter;
import ca.ibodrov.mica.server.exceptions.ConstraintViolationExceptionMapper;
import ca.ibodrov.mica.server.exceptions.DataAccessExceptionMapper;
import ca.ibodrov.mica.server.oidc.OidcResource;
import ca.ibodrov.mica.server.ui.SwaggerServlet;
import ca.ibodrov.mica.server.ui.UiServlet;
import ca.ibodrov.mica.server.ui.WhoamiResource;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.typesafe.config.Config;
import com.walmartlabs.concord.server.boot.FilterChainConfigurator;
import com.walmartlabs.ollie.config.ConfigurationProcessor;
import com.walmartlabs.ollie.config.Environment;
import com.walmartlabs.ollie.config.EnvironmentSelector;
import com.walmartlabs.ollie.config.OllieConfigurationModule;
import org.apache.shiro.realm.Realm;
import org.sonatype.siesta.Component;

import javax.inject.Named;
import javax.servlet.http.HttpServlet;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.walmartlabs.concord.server.Utils.bindJaxRsResource;

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

        newSetBinder(binder, HttpServlet.class).addBinding().to(UiServlet.class).in(SINGLETON);
        newSetBinder(binder, HttpServlet.class).addBinding().to(SwaggerServlet.class).in(SINGLETON);

        // filter chains

        newSetBinder(binder, FilterChainConfigurator.class).addBinding().to(MicaFilterChainConfigurator.class);

        // exception mappers

        newSetBinder(binder, Component.class).addBinding().to(DataAccessExceptionMapper.class);
        newSetBinder(binder, Component.class).addBinding().to(ConstraintViolationExceptionMapper.class);

        // security realms

        newSetBinder(binder, Realm.class).addBinding().to(MicaRealm.class);

        // jax-rs resources

        bindJaxRsResource(binder, ClientDataResource.class);
        bindJaxRsResource(binder, ClientEndpointResource.class);
        bindJaxRsResource(binder, ClientResource.class);
        bindJaxRsResource(binder, DocumentResource.class);
        bindJaxRsResource(binder, OidcResource.class);
        bindJaxRsResource(binder, ProfileResource.class);
        bindJaxRsResource(binder, SystemResource.class);
        bindJaxRsResource(binder, WhoamiResource.class);
        bindJaxRsResource(binder, EntityResource.class);

        // other beans

        binder.bind(UuidGenerator.class).in(SINGLETON);

        binder.bind(ClientDataImporter.class).in(SINGLETON);
        newSetBinder(binder, DocumentImporter.class).addBinding().to(ClientDataImporter.class);

        binder.bind(ProfileImporter.class).in(SINGLETON);
        newSetBinder(binder, DocumentImporter.class).addBinding().to(ProfileImporter.class);
    }

    private static Config loadDefaultConfig() {
        // TODO avoid re-reading the config
        Environment env = new EnvironmentSelector().select();
        return new ConfigurationProcessor("concord-server", env, null, null).process();
    }
}
