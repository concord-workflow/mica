package ca.ibodrov.mica.server.data.remote;

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

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.api.model.RenderRequest;
import ca.ibodrov.mica.server.data.ConcordSecretResolver;
import ca.ibodrov.mica.server.data.EntityFetcher;
import ca.ibodrov.mica.server.data.QueryParams;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.server.org.secret.SecretType;

import javax.inject.Inject;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.jetty.http.HttpHeader.AUTHORIZATION;
import static org.eclipse.jetty.http.HttpHeader.CONTENT_TYPE;

public class RemoteMicaEntityFetcher implements EntityFetcher {

    private static final String URI_SCHEME = "mica+remote";
    private static final String DEFAULT_ENTITY_KIND = "/mica+remote/object/v1";

    private final ConcordSecretResolver secretResolver;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    @Inject
    public RemoteMicaEntityFetcher(ConcordSecretResolver secretResolver, ObjectMapper objectMapper) {
        this.secretResolver = requireNonNull(secretResolver);
        this.objectMapper = requireNonNull(objectMapper);
        this.client = HttpClient.newHttpClient();
    }

    @Override
    public boolean isSupported(FetchRequest request) {
        return request.uri()
                .map(uri -> URI_SCHEME.equals(uri.getScheme()))
                .orElse(false);
    }

    @Override
    public Cursor fetch(FetchRequest request) {
        var uri = request.uri().orElseThrow(() -> new StoreException(URI_SCHEME + ":// URI is required"));
        var queryParams = new QueryParams(uri.getQuery());

        var viewName = assertViewName(uri);
        var parameters = parseViewParameters(queryParams);
        var insecure = queryParams.getFirst("insecure").map(Boolean::parseBoolean).orElse(false);
        var secret = queryParams.getFirst("secretRef").map(this::getSecret);

        var renderRequest = new RenderRequest(Optional.empty(), Optional.of(viewName), Optional.of(parameters));

        var remoteUri = UriBuilder.fromUri("https://localhost:8080/api/mica/v1/view/render")
                .scheme(insecure ? "http" : "https")
                .host(uri.getHost())
                .port(uri.getPort())
                .build();

        try {
            var req = HttpRequest.newBuilder()
                    .POST(BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(renderRequest)))
                    .uri(remoteUri)
                    .header(CONTENT_TYPE.lowerCaseName(), APPLICATION_JSON);

            secret.ifPresent(apiKey -> req.header(AUTHORIZATION.lowerCaseName(),
                    "Bearer " + new String(apiKey.getData(), UTF_8)));

            var resp = client.send(req.build(), BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                throw new StoreException("Failed to render a remote view: %s %s".formatted(resp.statusCode(),
                        Status.fromStatusCode(resp.statusCode())));
            }

            try (var body = resp.body()) {
                var renderedView = objectMapper.readValue(body, PartialEntity.class);

                var data = renderedView.data().get("data");
                if (data == null || data.isEmpty()) {
                    return Stream::empty;
                }

                return () -> parseResult(renderedView.name(), data);
            }
        } catch (ConnectException e) {
            throw new StoreException("Failed to connect to the remote Mica instance: %s".formatted(remoteUri));
        } catch (IOException e) {
            throw new StoreException(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StoreException("Interrupted");
        }
    }

    private JsonNode parseViewParameters(QueryParams queryParam) {
        var result = objectMapper.createObjectNode();
        queryParam.params().forEach((k, v) -> {
            if (k.startsWith("parameters.")) {
                var key = k.substring(11);
                result.set(key, TextNode.valueOf(v.get(0)));
            }
        });
        return result;
    }

    private BinaryDataSecret getSecret(String secretRef) {
        var secret = secretResolver.get(secretRef, SecretType.DATA);
        if (secret instanceof BinaryDataSecret apiKey) {
            return apiKey;
        } else {
            throw new StoreException("Invalid secretRef. Expected a string secret, got: " + secret.getClass());
        }
    }

    private static String assertViewName(URI uri) {
        var path = uri.getPath();
        if (!path.startsWith("/view/")) {
            throw new StoreException("Currently only /view/{viewName} URIs are supported");
        }

        var viewName = path.substring(6);
        if (viewName.startsWith("/")) {
            return viewName;
        }
        return "/" + viewName;
    }

    private static Stream<EntityLike> parseResult(String viewName, JsonNode viewData) {
        if (viewData.isObject()) {
            var data = toMap((ObjectNode) viewData);
            var entity = new PartialEntity(Optional.empty(), viewName, DEFAULT_ENTITY_KIND, Optional.empty(),
                    Optional.empty(), Optional.empty(), data);
            return Stream.of(entity);
        } else if (viewData.isArray()) {
            var result = new ArrayList<EntityLike>();
            for (var i = 0; i < viewData.size(); i++) {
                var item = viewData.get(i);

                Map<String, JsonNode> data;
                if (item.isObject()) {
                    data = toMap((ObjectNode) item);
                } else {
                    data = Map.of("data", item);
                }

                var name = viewName + "/" + i;
                var entity = new PartialEntity(Optional.empty(), name, DEFAULT_ENTITY_KIND, Optional.empty(),
                        Optional.empty(), Optional.empty(), data);
                result.add(entity);
            }
            return result.stream();
        } else {
            throw new StoreException("Failed to parse view data. Expected an object or an array, got %s"
                    .formatted(viewData.getNodeType()));
        }
    }

    private static Map<String, JsonNode> toMap(ObjectNode n) {
        var data = new HashMap<String, JsonNode>();
        n.fieldNames().forEachRemaining(key -> data.put(key, n.get(key)));
        return data;
    }
}
