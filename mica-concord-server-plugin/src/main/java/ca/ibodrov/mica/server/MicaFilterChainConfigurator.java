package ca.ibodrov.mica.server;

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

        // serve the liveness probe without authentication
        manager.createChain("/api/mica/v1/system", "anon");

        // OIDC login redirect and the logout URL should also be accessible for anyone
        manager.createChain("/api/mica/oidc/login", "anon");
        manager.createChain("/api/mica/oidc/logout", "anon");

        // delegate the rest of the requests to the Concord authenticating filter
        manager.addFilter("mica", concordDelegate);
        manager.addFilter("mica-user-session", new UserPrincipalContextProvider());
        manager.createChain("/api/mica/ui/**", "mica, mica-user-session");
        manager.createChain("/api/mica/oidc/**", "mica, mica-user-session");
        manager.createChain("/api/mica/v1/**", "mica, mica-user-session");
    }
}
