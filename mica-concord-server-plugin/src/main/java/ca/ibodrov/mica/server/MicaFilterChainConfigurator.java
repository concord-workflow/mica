package ca.ibodrov.mica.server;

import com.walmartlabs.concord.server.boot.FilterChainConfigurator;
import com.walmartlabs.concord.server.boot.filters.ConcordAuthenticatingFilter;
import ca.ibodrov.mica.server.oidc.OidcAuthenticatingFilter;
import org.apache.shiro.web.filter.mgt.FilterChainManager;

import javax.inject.Inject;

public class MicaFilterChainConfigurator implements FilterChainConfigurator {

    private final ConcordAuthenticatingFilter apiKeyDelegate;

    @Inject
    public MicaFilterChainConfigurator(ConcordAuthenticatingFilter apiKeyDelegate) {
        this.apiKeyDelegate = apiKeyDelegate;
    }

    @Override
    public void configure(FilterChainManager manager) {
        manager.addFilter("mica-oidc", new OidcAuthenticatingFilter());
        manager.createChain("/api/mica/ui/**", "mica-oidc");
        manager.createChain("/api/mica/oidc/**", "mica-oidc");

        manager.addFilter("mica-apikey", apiKeyDelegate);
        manager.createChain("/api/mica/v1/**", "mica-apikey");
    }
}
