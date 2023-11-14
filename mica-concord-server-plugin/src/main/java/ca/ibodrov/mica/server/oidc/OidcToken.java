package ca.ibodrov.mica.server.oidc;

import org.apache.shiro.authc.AuthenticationToken;

public record OidcToken(String token) implements AuthenticationToken {

    @Override
    public Object getPrincipal() {
        return this;
    }

    @Override
    public Object getCredentials() {
        return this;
    }
}
