package ca.ibodrov.mica.its;

/*-
 * ~~~~~~
 * Mica
 * ------
 * Copyright (C) 2023 - 2025 Mica Authors
 * ------
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ======
 */

import ca.ibodrov.concord.webapp.WebappPluginModule;
import ca.ibodrov.mica.db.MicaDatabaseModule;
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
