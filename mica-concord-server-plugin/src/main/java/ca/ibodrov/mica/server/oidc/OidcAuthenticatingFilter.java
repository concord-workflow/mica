package ca.ibodrov.mica.server.oidc;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

public class OidcAuthenticatingFilter extends AuthenticatingFilter {

    @Override
    protected AuthenticationToken createToken(ServletRequest request, ServletResponse response) {
        var userInfo = OidcUtils.getSessionOidcUserInfo((HttpServletRequest) request)
                .orElseThrow(() -> new AuthenticationException("No OIDC user info found in session"));
        return new OidcAuthenticationToken(userInfo);
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
        var userInfo = OidcUtils.getSessionOidcUserInfo((HttpServletRequest) request);
        if (userInfo.isEmpty()) {
            return true;
        }
        return executeLogin(request, response);
    }
}
