package ca.ibodrov.mica.standalone;

import ca.ibodrov.concord.webapp.WebappPluginModule;
import ca.ibodrov.mica.server.MicaPluginModule;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigResolveOptions;
import com.walmartlabs.concord.server.ConcordServer;
import com.walmartlabs.concord.server.plugins.oidc.OidcPluginModule;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MicaServer implements AutoCloseable {

    private final Map<String, String> config;
    private final Lock controlLock = new ReentrantLock();

    private ConcordServer server;

    public MicaServer(Map<String, String> config) {
        this.config = Map.copyOf(config);
    }

    public void start() throws Exception {
        controlLock.lock();
        try {
            var config = prepareConfig();
            var mica = new MicaServerModule(config);
            var oidc = new OidcPluginModule();
            var webapp = new WebappPluginModule();
            server = ConcordServer.withModules(mica, oidc, webapp)
                    .start();
        } finally {
            controlLock.unlock();
        }
    }

    public synchronized void stop() throws Exception {
        controlLock.lock();
        try {
            if (server != null) {
                server.stop();
            }
        } finally {
            controlLock.unlock();
        }
    }

    @Override
    public void close() throws Exception {
        this.stop();
    }

    private Config prepareConfig() {
        var micaConfig = ConfigFactory.parseMap(this.config);
        var defaultConfig = ConfigFactory
                .load("concord-server.conf", ConfigParseOptions.defaults(),
                        ConfigResolveOptions.defaults().setAllowUnresolved(true))
                .getConfig("concord-server");
        return micaConfig.withFallback(defaultConfig).resolve();
    }
}
