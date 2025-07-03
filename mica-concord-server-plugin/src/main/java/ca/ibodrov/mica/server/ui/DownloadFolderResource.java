package ca.ibodrov.mica.server.ui;

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

import ca.ibodrov.mica.api.model.EntityMetadata;
import ca.ibodrov.mica.server.YamlMapper;
import ca.ibodrov.mica.server.data.EntityStore;
import ca.ibodrov.mica.server.data.EntityStore.ListEntitiesRequest;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.sdk.rest.Resource;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.HttpHeaders.CONTENT_DISPOSITION;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

@Path("/api/mica/ui/downloadFolder")
public class DownloadFolderResource implements Resource {

    private final EntityStore entityStore;
    private final YamlMapper yamlMapper;

    @Inject
    public DownloadFolderResource(EntityStore entityStore, ObjectMapper objectMapper) {
        this.entityStore = requireNonNull(entityStore);
        this.yamlMapper = new YamlMapper(requireNonNull(objectMapper));
    }

    @GET
    public Response downloadFolder(@QueryParam("namePrefix") String namePrefix) {
        var output = new StreamingOutput() {
            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {
                try (var zip = new ZipOutputStream(new BufferedOutputStream(output))) {
                    var entities = entityStore.search(ListEntitiesRequest.nameStartsWith(namePrefix));
                    entities.forEach(entity -> addZipEntry(zip, entity));
                }
            }
        };

        return Response.ok(output)
                .header(CONTENT_DISPOSITION, "attachment; filename=\"folder.zip\"")
                .header(CONTENT_TYPE, "application/zip")
                .build();
    }

    private void addZipEntry(ZipOutputStream zip, EntityMetadata entityMetadata) {
        var doc = entityStore.getLatestEntityDoc(entityMetadata.id()).orElseGet(() -> {
            var entity = entityStore.getById(entityMetadata.id())
                    .orElseThrow(() -> ApiException.internalError("Error while archiving a folder"));
            try {
                return yamlMapper.prettyPrint(entity);
            } catch (IOException e) {
                throw ApiException.internalError("Error while archiving a folder");
            }
        });

        var name = entityMetadata.name();
        var lowerCaseName = entityMetadata.name().toLowerCase();
        if (!lowerCaseName.endsWith(".yml") && !lowerCaseName.endsWith(".yaml")) {
            name = name + ".yaml";
        }

        try {
            var entry = new ZipEntry(name);
            zip.putNextEntry(entry);
            zip.write(doc.getBytes(UTF_8));
            zip.closeEntry();
        } catch (IOException e) {
            throw ApiException.internalError("Error while archiving a folder");
        }
    }
}
