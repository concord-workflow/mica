package ca.ibodrov.mica.server.ui;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Optional;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

/**
 * Serves the UI resources SPA-style: files served as usual but unknown URLs
 * always return the content of index.html.
 */
@WebServlet("/mica/*")
public class UiServlet extends HttpServlet {

    private static final String RESOURCE_ROOT = "META-INF/mica-ui/";
    private static final String INDEX_HTML = "index.html";
    private static final String CHECKSUMS_FILE = "META-INF/mica-ui.checksums.cvs";

    private final Map<String, UiResource> resources;

    public UiServlet() {
        // a quick sanity check
        resources = loadResources(CHECKSUMS_FILE);
        Optional.ofNullable(resources.get(INDEX_HTML))
                .orElseThrow(() -> new RuntimeException(
                        "Missing the root UI resource: %s. Classpath issues?".formatted(RESOURCE_ROOT + INDEX_HTML)));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        var path = req.getPathInfo();

        if (path == null) {
            path = "";
        } else if (path.startsWith("/")) {
            path = path.substring(1);
        }

        if (path.isEmpty() || path.equals("/")) {
            path = "index.html";
        }

        try {
            var resource = Optional.ofNullable(resources.get(path))
                    .orElseGet(() -> resources.get(INDEX_HTML));

            var filePath = RESOURCE_ROOT + resource.path();
            try (var in = UiServlet.class.getClassLoader().getResourceAsStream(filePath)) {
                if (in == null) {
                    throw new RuntimeException("Resource not found: " + filePath);
                }

                resp.setHeader("Content-Type", resource.contentType());
                resp.setHeader("ETag", resource.eTag());

                var ifNoneMatch = req.getHeader("If-None-Match");
                if (resource.eTag().equals(ifNoneMatch)) {
                    resp.setStatus(304);
                } else {
                    resp.setStatus(200);
                    ByteStreams.copy(in, resp.getOutputStream());
                }
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
        return Optional.ofNullable(switch (ext) {
            case "html" -> "text/html";
            case "css" -> "text/css";
            case "js" -> "text/javascript";
            case "svg" -> "image/svg+xml";
            case "woff" -> "font/woff";
            case "woff2" -> "font/woff2";
            default -> null;
        });
    }

    private Map<String, UiResource> loadResources(String file) {
        var resources = ImmutableMap.<String, UiResource>builder();

        var cl = UiServlet.class.getClassLoader();
        try (var in = cl.getResourceAsStream(file)) {
            if (in == null) {
                throw new RuntimeException(file + " file not found. Classpath or build issues?");
            }

            try (var reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    if (line.startsWith("#")) {
                        continue;
                    }

                    var items = line.split(",");
                    if (items.length != 2) {
                        throw new RuntimeException(file + " file, invalid line: " + line);
                    }

                    var path = items[0];
                    var eTag = items[1];
                    var contentType = getContentType(path)
                            .orElseThrow(() -> new RuntimeException("Can't determine Content-Type for " + path));

                    resources.put(path, new UiResource(path, contentType, eTag));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return resources.build();
    }

    private record UiResource(String path, String contentType, String eTag) {
    }
}
