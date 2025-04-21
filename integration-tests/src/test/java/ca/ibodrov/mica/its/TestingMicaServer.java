package ca.ibodrov.mica.its;

import ca.ibodrov.concord.webapp.WebappPluginModule;
import ca.ibodrov.mica.server.MicaPluginModule;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.typesafe.config.Config;
import com.walmartlabs.concord.it.testingserver.TestingConcordServer;
import com.walmartlabs.concord.server.plugins.oidc.OidcPluginModule;
import com.walmartlabs.concord.server.sdk.rest.Component;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.google.inject.multibindings.Multibinder.newSetBinder;

/**
 * A helper class for running concord-server plus Mica. Used in tests. For a
 * locally runnable version see {@link ca.ibodrov.mica.its.LocalMicaServer}.
 */
public class TestingMicaServer extends TestingConcordServer {

    public static TestingMicaServer withFakeOidc(PostgreSQLContainer<?> db, int port) {
        var authServerUri = "http://localhost:12345/fake";
        var config = Map.of("mica.oidc.logoutEndpoint", "%s/login/signout".formatted(authServerUri));
        return new TestingMicaServer(db, port, config);
    }

    public TestingMicaServer(PostgreSQLContainer<?> db, int port, Map<String, String> extraConfiguration) {
        super(db, port, extraConfiguration, extraModules());
    }

    private static List<Function<Config, Module>> extraModules() {
        return List.of(
                MicaPluginModule::new,
                _cfg -> new WebappPluginModule(),
                _cfg -> new LocalServerModule(),
                _cfg -> new OidcPluginModule());
    }

    public static class LocalServerModule implements Module {

        @Override
        public void configure(Binder binder) {
            newSetBinder(binder, Component.class).addBinding().to(LocalExceptionMapper.class);
        }
    }
}
