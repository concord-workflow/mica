package ca.ibodrov.mica.server;

import com.walmartlabs.concord.server.boot.filters.AuthenticationHandler;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import java.io.IOException;

public class MicaAuthenticationHandler implements AuthenticationHandler {

    @Override
    public boolean onAccessDenied(ServletRequest request, ServletResponse response) throws IOException {
        var req = (HttpServletRequest) request;
        var requestUri = req.getRequestURI();
        if (requestUri != null && requestUri.startsWith("/api/mica/")) {
            var resp = (HttpServletResponse) response;
            resp.setContentType(MediaType.APPLICATION_JSON);
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "{\"message\": \"Unauthorized\"}");
            return true;
        }

        return false;
    }
}
