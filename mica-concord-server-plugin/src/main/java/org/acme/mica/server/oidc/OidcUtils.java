package org.acme.mica.server.oidc;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

public final class OidcUtils {

    private static final String OIDC_USER_INFO_KEY = "oidcUserInfo";

    public static Optional<OidcUserInfo> getSessionOidcUserInfo(HttpServletRequest request) {
        return Optional.ofNullable(request.getSession(false))
                .flatMap(session -> Optional.ofNullable((OidcUserInfo) session.getAttribute(OIDC_USER_INFO_KEY)));
    }

    public static void setSessionOidcUserInfo(HttpServletRequest request, OidcUserInfo oidcUserInfo) {
        request.getSession(true).setAttribute(OIDC_USER_INFO_KEY, oidcUserInfo);
    }

    public static void clearSessionOidcUserInfo(HttpServletRequest request) {
        Optional.ofNullable(request.getSession(false))
                .ifPresent(session -> session.removeAttribute(OIDC_USER_INFO_KEY));
    }

    private OidcUtils() {
    }
}
