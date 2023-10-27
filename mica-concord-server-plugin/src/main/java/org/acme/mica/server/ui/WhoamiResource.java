package org.acme.mica.server.ui;

import com.walmartlabs.concord.server.security.PrincipalUtils;
import org.acme.mica.server.MicaPrincipal;
import org.acme.mica.server.oidc.OidcToken;
import org.sonatype.siesta.Resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.UNAUTHORIZED;

@Path("/api/mica/ui/whoami")
public class WhoamiResource implements Resource {

    @GET
    @Produces(APPLICATION_JSON)
    public WhoamiResponse whoami() {
        var principal = PrincipalUtils.getCurrent(MicaPrincipal.class);
        if (principal == null) {
            throw new WebApplicationException(UNAUTHORIZED);
        }
        return new WhoamiResponse(principal.username());
    }

    public record WhoamiResponse(String username) {
    }
}
