package org.acme.mica.server;

import com.walmartlabs.concord.server.security.PrincipalUtils;
import org.acme.mica.server.oidc.OidcAuthenticationToken;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

import java.util.List;

public class MicaRealm extends AuthorizingRealm {

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof OidcAuthenticationToken;
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        MicaPrincipal principal = principals.oneByType(MicaPrincipal.class);
        if (principal == null) {
            return null;
        }
        return PrincipalUtils.toAuthorizationInfo(principals);
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        var userInfo = ((OidcAuthenticationToken) token).userInfo();
        MicaPrincipal principal = new MicaPrincipal(userInfo.email());
        return new SimpleAccount(List.of(principal, token), userInfo, "mica-oidc");
    }
}
