package org.acme.mica.server.oidc;

import org.acme.mica.server.oidc.OidcClient.OidcUserInfo;
import org.apache.shiro.authc.AuthenticationToken;

public record OidcAuthenticationToken(OidcUserInfo userInfo) implements AuthenticationToken {

    @Override
    public Object getPrincipal() {
        return userInfo;
    }

    @Override
    public Object getCredentials() {
        return userInfo;
    }
}
