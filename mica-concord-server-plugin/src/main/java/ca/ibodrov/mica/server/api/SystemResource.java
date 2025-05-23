package ca.ibodrov.mica.server.api;

/*-
 * ~~~~~~
 * Mica
 * ------
 * Copyright (C) 2023 - 2025 Mica Authors
 * ------
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ======
 */

import ca.ibodrov.mica.api.model.SystemInfo;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

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
    @Operation(summary = "Returns Mica's system info (e.g. version)", operationId = "getSystemInfo")
    public SystemInfo getSystemInfo() {
        return systemInfo;
    }

}
