package ca.ibodrov.mica.server;

import com.walmartlabs.concord.server.security.UserPrincipal;
import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static javax.ws.rs.core.Response.Status.UNAUTHORIZED;

public class UserPrincipalContextProvider extends HttpFilter {

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        if (ResteasyProviderFactory.getInstance().getContextData(UserPrincipal.class) != null) {
            chain.doFilter(req, res);
            return;
        }

        var principal = UserPrincipal.getCurrent();
        if (principal == null) {
            res.sendError(UNAUTHORIZED.getStatusCode());
            var session = req.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            return;
        }

        ResteasyContext.pushContext(UserPrincipal.class, principal);

        chain.doFilter(req, res);
    }
}
