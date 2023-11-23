package ca.ibodrov.mica.its;

import ca.ibodrov.mica.server.MicaModule;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.typesafe.config.Config;
import com.walmartlabs.concord.it.testingserver.TestingConcordServer;
import org.sonatype.siesta.Component;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A helper class for running concord-server plus Mica. Used in tests. For a
 * locally runnable version see {@link ca.ibodrov.mica.its.LocalMicaServer}.
 */
public class TestingMicaServer extends TestingConcordServer {

    public static TestingMicaServer withFakeOidc(PostgreSQLContainer<?> db, int port) {
        var authServerUri = "http://localhost:12345/fake";
        var config = Map.of(
                "mica.oidc.clientId", "fake",
                "mica.oidc.clientSecret", "fake",
                "mica.oidc.authorizationEndpoint", "%s/oauth2/v1/authorize".formatted(authServerUri),
                "mica.oidc.tokenEndpoint", "%s/oauth2/v1/token".formatted(authServerUri),
                "mica.oidc.userinfoEndpoint", "%s/oauth2/v1/userinfo".formatted(authServerUri),
                "mica.oidc.logoutEndpoint", "%s/login/signout".formatted(authServerUri));
        return new TestingMicaServer(db, port, config);
    }

    public TestingMicaServer(PostgreSQLContainer<?> db, int port, Map<String, String> extraConfiguration) {
        super(db, port, extraConfiguration, extraModules());
    }

    private static List<Function<Config, Module>> extraModules() {
        return List.of(_cfg -> new LocalServerModule(), MicaModule::new);
    }

    public static class LocalServerModule implements Module {

        @Override
        public void configure(Binder binder) {
            Multibinder.newSetBinder(binder, Component.class).addBinding().to(LocalExceptionMapper.class);
        }
    }
}
