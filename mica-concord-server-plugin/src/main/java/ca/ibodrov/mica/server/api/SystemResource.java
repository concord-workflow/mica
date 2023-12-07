package ca.ibodrov.mica.server.api;

import ca.ibodrov.mica.api.model.SystemInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.sonatype.siesta.Resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.io.IOException;
import java.util.Properties;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Tag(name = "System")
@Path("/api/mica/v1/system")
@Produces(APPLICATION_JSON)
public class SystemResource implements Resource {

    private final SystemInfo systemInfo;

    public SystemResource() {
        var gitProperties = new Properties();
        try {
            gitProperties.load(SystemResource.class.getResourceAsStream("/ca/ibodrov/mica/server/git.properties"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        var version = requireNonNull(gitProperties.getProperty("git.commit.id.describe"));
        var commitId = requireNonNull(gitProperties.getProperty("git.commit.id"));
        systemInfo = new SystemInfo(version, commitId);
    }

    @GET
    @Operation(description = "Returns Mica's system info (e.g. version)", operationId = "getSystemInfo")
    public SystemInfo getSystemInfo() {
        return systemInfo;
    }

}
