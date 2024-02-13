package ca.ibodrov.mica.server.ui;

import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.security.UserPrincipal;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/api/mica/ui/whoami")
public class WhoamiResource implements Resource {

    @GET
    @Produces(APPLICATION_JSON)
    public WhoamiResponse whoami(@Context UserPrincipal session) {
        return new WhoamiResponse(session.getUsername());
    }

    public record WhoamiResponse(String username) {
    }
}
