package ca.ibodrov.mica.server.oidc;

import com.walmartlabs.ollie.config.Config;
import org.apache.shiro.SecurityUtils;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;

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
    public Response login(@Context HttpServletRequest request,
                          @Context UriInfo uriInfo) {

        var redirectUri = UriBuilder.fromUri(uriInfo.getBaseUri())
                .path("/api/service/oidc/auth")
                .queryParam("from", "/mica/")
                .build();

        return Response.temporaryRedirect(redirectUri).build();
    }

    @GET
    @Path("/logout")
    public Response logout(@Context HttpServletRequest request,
                           @Context UriInfo uriInfo) {

        // TODO support OIDC SLO with id_token_hint

        var redirectUri = createRedirectUri(uriInfo);

        var logoutUri = UriBuilder.fromUri(logoutEndpoint)
                .queryParam("fromURI", redirectUri)
                .build();

        SecurityUtils.getSubject().logout();

        return Response.temporaryRedirect(logoutUri).build();
    }

    private URI createRedirectUri(UriInfo uriInfo) {
        return UriBuilder.fromUri(uriInfo.getBaseUri())
                .path("/api/mica/oidc/callback")
                .build();
    }
}
