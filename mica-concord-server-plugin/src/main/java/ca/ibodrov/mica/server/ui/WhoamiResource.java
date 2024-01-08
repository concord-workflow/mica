package ca.ibodrov.mica.server.ui;

import ca.ibodrov.mica.server.MicaPrincipal;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.security.PrincipalUtils;
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.oidc.profile.OidcProfile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import java.util.Optional;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.UNAUTHORIZED;

@Path("/api/mica/ui/whoami")
public class WhoamiResource implements Resource {

    @GET
    @Produces(APPLICATION_JSON)
    public WhoamiResponse whoami(@Context HttpServletRequest request, @Context HttpServletResponse response) {
        var principal = PrincipalUtils.getCurrent(MicaPrincipal.class);
        if (principal == null) {
            // login via Concord's OIDC
            var profileManager = new ProfileManager<OidcProfile>(new JEEContext(request, response));
            var profile = profileManager.get(true).orElseThrow(() -> {
                Optional.ofNullable(request.getSession(false))
                        .ifPresent(HttpSession::invalidate);
                return new WebApplicationException(UNAUTHORIZED);
            });
            return new WhoamiResponse(profile.getEmail());
        }
        return new WhoamiResponse(principal.username());
    }

    public record WhoamiResponse(String username) {
    }
}
