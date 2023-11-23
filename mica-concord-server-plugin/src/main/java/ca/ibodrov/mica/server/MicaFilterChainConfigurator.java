package ca.ibodrov.mica.server;

import com.walmartlabs.concord.server.boot.FilterChainConfigurator;
import com.walmartlabs.concord.server.boot.filters.ConcordAuthenticatingFilter;
import org.apache.shiro.web.filter.mgt.FilterChainManager;

import javax.inject.Inject;

public class MicaFilterChainConfigurator implements FilterChainConfigurator {

    private final ConcordAuthenticatingFilter concordDelegate;

    @Inject
    public MicaFilterChainConfigurator(ConcordAuthenticatingFilter apiKeyDelegate) {
        this.concordDelegate = apiKeyDelegate;
    }

    @Override
    public void configure(FilterChainManager manager) {
        // serve static UI resources without authentication
        manager.createChain("/mica/**", "anon");
        manager.createChain("/api/mica/swagger.json", "anon");

        // whoami must be accessible without authentication
        manager.createChain("/api/mica/ui/whoami", "anon");
        // OIDC login redirect should also be accessible for anyone
        manager.createChain("/api/mica/oidc/login", "anon");

        manager.addFilter("mica", concordDelegate);
        manager.createChain("/api/mica/oidc/**", "mica");
        manager.createChain("/api/mica/v1/**", "mica");
    }
}
