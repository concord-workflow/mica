package ca.ibodrov.mica.server;

import com.walmartlabs.concord.server.security.UserPrincipal;
import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.oidc.profile.OidcProfile;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

import static ca.ibodrov.mica.server.data.UserEntryUtils.user;
import static javax.ws.rs.core.Response.Status.UNAUTHORIZED;

public class UserPrincipalContextProvider extends HttpFilter {

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        if (ResteasyProviderFactory.getInstance().getContextData(UserPrincipal.class) != null) {
            chain.doFilter(req, res);
            return;
        }

        var principal = Optional.ofNullable(UserPrincipal.getCurrent())
                .or(() -> tryOidcProfile(req, res));

        if (principal.isEmpty()) {
            res.sendError(UNAUTHORIZED.getStatusCode());
            req.getSession(false).invalidate();
            return;
        }

        // hm... where is ResteasyProviderFactory...pushContext ?
        ResteasyContext.pushContext(UserPrincipal.class, principal.get());

        chain.doFilter(req, res);
    }

    private static Optional<UserPrincipal> tryOidcProfile(HttpServletRequest request, HttpServletResponse response) {
        var profileManager = new ProfileManager<OidcProfile>(new JEEContext(request, response));
        return profileManager.get(true).map(p -> new UserPrincipal("oidc", user(p.getEmail())));
    }
}
