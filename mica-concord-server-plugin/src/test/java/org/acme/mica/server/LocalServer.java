package org.acme.mica.server;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.walmartlabs.concord.it.testingserver.TestingConcordServer;
import org.sonatype.siesta.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.inject.multibindings.Multibinder.newSetBinder;

public class LocalServer {

    public static void main(String[] args) throws Exception {
        var authServerUri = assertEnvVar("TEST_OIDC_AUTHSERVER");

        var config = Map.of(
                "mica.oidc.clientId", assertEnvVar("TEST_OIDC_CLIENTID"),
                "mica.oidc.clientSecret", assertEnvVar("TEST_OIDC_SECRET"),
                "mica.oidc.authorizationEndpoint", "%s/oauth2/v1/authorize".formatted(authServerUri),
                "mica.oidc.tokenEndpoint", "%s/oauth2/v1/token".formatted(authServerUri),
                "mica.oidc.userinfoEndpoint", "%s/oauth2/v1/userinfo".formatted(authServerUri),
                "mica.oidc.logoutEndpoint", "%s/login/signout".formatted(authServerUri));

        try (var server = new TestingConcordServer(config, List.of(_cfg -> new LocalServerModule(), MicaModule::new))) {
            server.start();

            System.out.println("""
                    ==============================================================

                      UI (hosted): http://localhost:8001/mica/
                      UI (dev): http://localhost:5173/mica/ (launched separately)

                    ==============================================================
                    """);
            Thread.currentThread().join();
        }
    }

    private static String assertEnvVar(String key) {
        return Optional.ofNullable(System.getenv(key))
                .orElseThrow(() -> new RuntimeException("Missing %s env var".formatted(key)));
    }

    public static class LocalServerModule implements Module {

        @Override
        public void configure(Binder binder) {
            newSetBinder(binder, Component.class).addBinding().to(LocalExceptionMapper.class);
        }
    }
}
