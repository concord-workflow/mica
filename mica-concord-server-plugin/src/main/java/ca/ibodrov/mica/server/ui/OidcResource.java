package ca.ibodrov.mica.server.ui;

import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.ollie.config.Config;
import org.apache.shiro.SecurityUtils;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.util.Optional;

import static ca.ibodrov.mica.server.api.ApiUtils.nonBlank;
import static java.util.Objects.requireNonNull;

@Path("/api/mica/oidc")
public class OidcResource implements Resource {

    private final String logoutEndpoint;

    @Inject
    public OidcResource(@Config("mica.oidc.logoutEndpoint") String logoutEndpoint) {
        this.logoutEndpoint = requireNonNull(logoutEndpoint);
    }

    @GET
    @Path("/logout")
    // TODO support OIDC SLO with id_token_hint
    public Response logout(@Context HttpServletRequest request,
                           @QueryParam("from") String from,
                           @Context UriInfo uriInfo) {

        var effectiveFrom = Optional.ofNullable(nonBlank(from))
                .orElseGet(() -> UriBuilder.fromUri(uriInfo.getBaseUri())
                        .path("/mica/")
                        .build()
                        .toASCIIString());

        var redirectUri = UriBuilder.fromUri(logoutEndpoint)
                .queryParam("from", effectiveFrom)
                .build();

        SecurityUtils.getSubject().logout();

        return Response.temporaryRedirect(redirectUri).build();
    }
}
