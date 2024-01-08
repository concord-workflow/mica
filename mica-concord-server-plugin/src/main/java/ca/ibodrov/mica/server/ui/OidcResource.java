package ca.ibodrov.mica.server.ui;

import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.ollie.config.Config;
import org.apache.shiro.SecurityUtils;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import static java.util.Objects.requireNonNull;

@Path("/api/mica/oidc")
public class OidcResource implements Resource {

    private final String logoutEndpoint;

    @Inject
    public OidcResource(@Config("mica.oidc.logoutEndpoint") String logoutEndpoint) {
        this.logoutEndpoint = requireNonNull(logoutEndpoint);
    }

    @GET
    @Path("/login")
    public Response login(@Context HttpServletRequest request, @Context UriInfo uriInfo) {
        var from = UriBuilder.fromUri(uriInfo.getBaseUri()).path("/mica/").build();
        var redirectUri = UriBuilder.fromUri(uriInfo.getBaseUri())
                .path("/api/service/oidc/auth")
                .queryParam("from", from)
                .build();
        return Response.temporaryRedirect(redirectUri).build();
    }

    @GET
    @Path("/logout")
    // TODO support OIDC SLO with id_token_hint
    public Response logout(@Context HttpServletRequest request, @Context UriInfo uriInfo) {
        SecurityUtils.getSubject().logout();
        var from = UriBuilder.fromUri(uriInfo.getBaseUri()).path("/mica/").build();
        var logoutUri = UriBuilder.fromUri(logoutEndpoint)
                .queryParam("fromURI", from)
                .build();
        return Response.temporaryRedirect(logoutUri).build();
    }
}
