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

import ca.ibodrov.mica.server.data.EntityKindStore;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.annotations.VisibleForTesting;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.util.function.Function;

import static ca.ibodrov.mica.server.data.BuiltinSchemas.*;
import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/api/mica/ui/editorSchema")
@Produces(APPLICATION_JSON)
public class EditorSchemaResource implements Resource {

    private final EntityKindStore entityKindStore;

    @Inject
    public EditorSchemaResource(EntityKindStore entityKindStore) {
        this.entityKindStore = requireNonNull(entityKindStore);
    }

    @GET
    @Path("{kind:.*}")
    @Validate
    public JsonNode getSchemaForEntityKind(@Context UriInfo uriInfo, @PathParam("kind") String kind) {
        if (kind == null || kind.length() < 3) {
            throw ApiException.badRequest("Invalid kind: " + kind);
        }

        if (!kind.startsWith("/")) {
            kind = "/" + kind;
        }

        var schema = entityKindStore.getSchemaForKind(kind)
                .orElseThrow(() -> ApiException.notFound("Schema not found"));

        var baseUri = uriInfo.getBaseUriBuilder()
                .path(EditorSchemaResource.class)
                .build()
                .toString();

        // replace internal schema references with references accessible from the UI
        var standardPropertiesExternalRef = baseUri + STANDARD_PROPERTIES_V1;
        schema = findReplace(schema, "$ref",
                s -> s.replaceFirst("^" + STANDARD_PROPERTIES_REF + "$", standardPropertiesExternalRef)
                        .replaceFirst("^" + JSON_SCHEMA_REF + "$", EXTERNAL_JSON_SCHEMA_REF));

        // the schema's metadata
        schema = set(schema, "$id", kind);
        schema = set(schema, "$schema", EXTERNAL_JSON_SCHEMA_REF);

        return schema;
    }

    @VisibleForTesting
    static JsonNode findReplace(JsonNode node, String key, Function<String, String> replacer) {
        if (node == null || node.isNull()) {
            return node;
        }

        // depth-first search

        if (node.isObject()) {
            node.fieldNames().forEachRemaining(fieldName -> {
                var child = findReplace(node.get(fieldName), key, replacer);
                var updatedNode = (ObjectNode) node;
                updatedNode.set(fieldName, child);
            });
        } else if (node.isArray()) {
            var array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                var child = findReplace(array.get(i), key, replacer);
                array.set(i, child);
            }
        }

        var ref = node.get(key);
        if (ref != null && ref.isTextual()) {
            var updatedNode = (ObjectNode) node;
            updatedNode.set(key, new TextNode(replacer.apply(ref.asText())));
        }

        return node;
    }

    private static JsonNode set(JsonNode node, String key, String value) {
        if (node == null || node.isNull() || !node.isObject()) {
            return node;
        }
        ((ObjectNode) node).set(key, new TextNode(value));
        return node;
    }
}
