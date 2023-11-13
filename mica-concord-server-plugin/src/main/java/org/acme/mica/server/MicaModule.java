package org.acme.mica.server;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.typesafe.config.Config;
import com.walmartlabs.concord.server.boot.FilterChainConfigurator;
import com.walmartlabs.concord.server.security.UnauthenticatedExceptionMapper;
import com.walmartlabs.ollie.config.ConfigurationProcessor;
import com.walmartlabs.ollie.config.Environment;
import com.walmartlabs.ollie.config.EnvironmentSelector;
import com.walmartlabs.ollie.config.OllieConfigurationModule;
import org.acme.mica.db.MicaDatabaseModule;
import org.acme.mica.server.api.*;
import org.acme.mica.server.oidc.OidcResource;
import org.acme.mica.server.ui.UiServlet;
import org.acme.mica.server.ui.WhoamiResource;
import org.apache.shiro.realm.Realm;

import javax.inject.Named;
import javax.servlet.http.HttpServlet;
import javax.ws.rs.ext.ExceptionMapper;

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
        binder.install(new OllieConfigurationModule("org.acme.mica.server", config));

        // servlets

        newSetBinder(binder, HttpServlet.class).addBinding().to(UiServlet.class).in(SINGLETON);

        // filter chains

        newSetBinder(binder, FilterChainConfigurator.class).addBinding().to(MicaFilterChainConfigurator.class);

        // exception mappers

        newSetBinder(binder, ExceptionMapper.class).addBinding().to(UnauthenticatedExceptionMapper.class);

        // security realms

        newSetBinder(binder, Realm.class).addBinding().to(MicaRealm.class);

        // jax-rs resources

        bindJaxRsResource(binder, WhoamiResource.class);
        bindJaxRsResource(binder, OidcResource.class);
        bindJaxRsResource(binder, DocumentResource.class);
        bindJaxRsResource(binder, ClientResource.class);
        bindJaxRsResource(binder, ClientDataResource.class);

        // other beans

        binder.bind(UuidGenerator.class).in(SINGLETON);

        binder.bind(ClientDataImporter.class).in(SINGLETON);
        newSetBinder(binder, DocumentImporter.class).addBinding().to(ClientDataImporter.class);
    }

    private static Config loadDefaultConfig() {
        // TODO avoid re-reading the config
        Environment env = new EnvironmentSelector().select();
        return new ConfigurationProcessor("concord-server", env, null, null).process();
    }
}
