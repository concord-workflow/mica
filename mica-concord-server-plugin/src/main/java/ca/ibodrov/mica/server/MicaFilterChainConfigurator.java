package ca.ibodrov.mica.server;

import com.walmartlabs.concord.server.boot.FilterChainConfigurator;
import com.walmartlabs.concord.server.boot.filters.ConcordAuthenticatingFilter;
import org.apache.shiro.web.filter.mgt.FilterChainManager;

import javax.inject.Inject;

public class MicaFilterChainConfigurator implements FilterChainConfigurator {

    private final ConcordAuthenticatingFilter concordDelegate;

    @Inject
    public MicaFilterChainConfigurator(ConcordAuthenticatingFilter concordDelegate) {
        this.concordDelegate = concordDelegate;
    }

    @Override
    public void configure(FilterChainManager manager) {
        // serve static UI resources without authentication
        manager.createChain("/mica/**", "anon");
        manager.createChain("/api/mica/swagger.json", "anon");

        // OIDC login redirect and the logout URL should also be accessible for anyone
        manager.createChain("/api/mica/oidc/login", "anon");
        manager.createChain("/api/mica/oidc/logout", "anon");

        // delegate the rest of the requests to the Concord authenticating filter
        manager.addFilter("mica", concordDelegate);
        manager.addFilter("mica-user-session", new UserPrincipalContextProvider());
        manager.createChain("/api/mica/ui/whoami", "mica-user-session");
        manager.createChain("/api/mica/oidc/**", "mica, mica-user-session");
        manager.createChain("/api/mica/v1/**", "mica, mica-user-session");
    }
}
