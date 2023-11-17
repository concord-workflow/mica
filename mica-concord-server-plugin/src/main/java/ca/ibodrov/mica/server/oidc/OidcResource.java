package ca.ibodrov.mica.server.oidc;

import com.walmartlabs.ollie.config.Config;
import org.apache.shiro.SecurityUtils;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.Response.Status.UNAUTHORIZED;

@Path("/api/mica/oidc")
public class OidcResource implements Resource {

    private final String clientId;
    private final String authorizationEndpoint;
    private final String userinfoEndpoint;
    private final String logoutEndpoint;
    private final OidcClient oidcClient;

    @Inject
    public OidcResource(@Config("mica.oidc.id") String clientId,
                        @Config("mica.oidc.clientSecret") String clientSecret,
                        @Config("mica.oidc.authorizationEndpoint") String authorizationEndpoint,
                        @Config("mica.oidc.userinfoEndpoint") String userinfoEndpoint,
                        @Config("mica.oidc.logoutEndpoint") String logoutEndpoint,
                        @Config("mica.oidc.tokenEndpoint") String tokenEndpoint) {

        this.clientId = requireNonNull(clientId);
        this.authorizationEndpoint = requireNonNull(authorizationEndpoint);
        this.userinfoEndpoint = requireNonNull(userinfoEndpoint);
        this.logoutEndpoint = requireNonNull(logoutEndpoint);
        try {
            this.oidcClient = new OidcClient(
                    new URI(requireNonNull(tokenEndpoint)),
                    new URI(requireNonNull(this.userinfoEndpoint)),
                    requireNonNull(clientId),
                    requireNonNull(clientSecret));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @GET
    @Path("/login")
    public Response login(@Context HttpServletRequest request,
                          @Context UriInfo uriInfo) {
        OidcUtils.clearSessionOidcUserInfo(request);

        var redirectUri = createRedirectUri(uriInfo);

        var authorizeUri = UriBuilder.fromUri(authorizationEndpoint)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email")
                .queryParam("state", UUID.randomUUID().toString())
                .build();

        return Response.temporaryRedirect(authorizeUri).build();
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

        OidcUtils.clearSessionOidcUserInfo(request);
        SecurityUtils.getSubject().logout();

        return Response.temporaryRedirect(logoutUri).build();
    }

    @GET
    @Path("/callback")
    public Response callback(@Context HttpServletRequest request,
                             @Context UriInfo uriInfo,
                             @QueryParam("code") String code,
                             @QueryParam("state") String state /* TODO */) {
        var redirectUri = createRedirectUri(uriInfo);
        var response = oidcClient.exchangeCodeForAccessToken(code, redirectUri);
        var userInfo = oidcClient
                .fetchUserInfo(response.accessToken().orElseThrow(() -> new WebApplicationException(UNAUTHORIZED)));
        OidcUtils.setSessionOidcUserInfo(request, userInfo);
        return Response.temporaryRedirect(URI.create("/mica/")).build();
    }

    private URI createRedirectUri(UriInfo uriInfo) {
        return UriBuilder.fromUri(uriInfo.getBaseUri())
                .path("/api/mica/oidc/callback")
                .build();
    }
}
