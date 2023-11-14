package org.acme.mica.server.ui;

import com.google.common.io.ByteStreams;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;
import java.io.IOException;
import java.util.Optional;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

/**
 * Serves the UI resources SPA-style: files served as usual but unknown URLs
 * always return the content of index.html.
 */
@WebServlet("/mica/*")
public class UiServlet extends HttpServlet {

    private static final String RESOURCE_ROOT = "META-INF/mica-ui/";
    private static final String INDEX_HTML = RESOURCE_ROOT + "index.html";
    private static final String TEXT_HTML_TYPE = "text/html";

    public UiServlet() {
        var cl = UiServlet.class.getClassLoader();
        if (cl.getResource(INDEX_HTML) == null) {
            throw new RuntimeException("Missing the root UI resource: %s. Classpath issues?".formatted(INDEX_HTML));
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
        var path = req.getPathInfo();

        if (path == null) {
            path = "";
        } else if (path.startsWith("/")) {
            path = path.substring(1);
        }

        if (path.isEmpty() || path.equals("/")) {
            path = "index.html";
        }

        var contentType = getContentType(path);
        try {
            var cl = UiServlet.class.getClassLoader();
            var in = cl.getResourceAsStream(RESOURCE_ROOT + path);
            if (in == null) {
                in = cl.getResourceAsStream(INDEX_HTML);
                contentType = Optional.of(TEXT_HTML_TYPE);
            }
            assert in != null;
            assert contentType.isPresent();
            try {
                resp.setStatus(200);
                resp.setHeader("Content-Type", contentType.get());
                ByteStreams.copy(in, resp.getOutputStream());
            } finally {
                in.close();
            }
        } catch (IOException e) {
            throw new WebApplicationException(INTERNAL_SERVER_ERROR);
        }
    }

    private static Optional<String> getContentType(String fileName) {
        var extIdx = fileName.lastIndexOf('.');
        if (extIdx < 2 || extIdx >= fileName.length() - 1) {
            return Optional.empty();
        }
        var ext = fileName.substring(extIdx + 1);
        return Optional.of(switch (ext) {
            case "html" -> "text/html";
            case "css" -> "text/css";
            case "js" -> "text/javascript";
            case "svg" -> "image/svg+xml";
            case "woff" -> "font/woff";
            case "woff2" -> "font/woff2";
            default -> null;
        });
    }
}
