package ca.ibodrov.mica.server.api;

import ca.ibodrov.mica.api.model.SystemInfo;
import org.sonatype.siesta.Resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.io.IOException;
import java.util.Properties;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/api/mica/v1/system")
@Produces(APPLICATION_JSON)
public class SystemResource implements Resource {

    private final SystemInfo systemInfo;

    public SystemResource() {
        var versionProps = new Properties();
        try {
            versionProps.load(SystemResource.class.getResourceAsStream("/org/acme/mica/server/version.properties"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        var version = versionProps.getProperty("mica.version");
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("Missing mica.version property");
        }
        systemInfo = new SystemInfo(version);
    }

    @GET
    public SystemInfo getSystemInfo() {
        return systemInfo;
    }

}
