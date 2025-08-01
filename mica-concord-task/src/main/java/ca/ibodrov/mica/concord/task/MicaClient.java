package ca.ibodrov.mica.concord.task;

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

import ca.ibodrov.mica.api.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.*;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static java.net.http.HttpResponse.BodyHandlers.ofInputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class MicaClient {

    private static final Logger log = LoggerFactory.getLogger(MicaClient.class);

    private final HttpClient client;
    private final URI baseUri;
    private final Authorization authorization;
    private final ObjectMapper objectMapper;
    private final String userAgent;
    private final Duration requestTimeout;

    public MicaClient(HttpClient client,
                      URI baseUri,
                      Authorization authorization,
                      String userAgent,
                      ObjectMapper objectMapper,
                      Duration requestTimeout) {

        this.client = requireNonNull(client);
        this.baseUri = requireNonNull(baseUri);
        this.authorization = requireNonNull(authorization);
        this.userAgent = requireNonNull(userAgent);
        this.objectMapper = requireNonNull(objectMapper);
        this.requestTimeout = requireNonNull(requestTimeout);
    }

    public BatchOperationResult apply(BatchOperationRequest body) throws ApiException {
        var request = newRequest("/api/mica/v1/batch")
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofByteArray(serialize(body)))
                .build();
        var response = send(request, ofInputStream());
        return parseResponseAsJson(response, BatchOperationResult.class);
    }

    public Optional<Entity> getEntityById(EntityId entityId, @Nullable Instant updatedAt) throws ApiException {
        var qp = queryParameters("updatedAt", updatedAt);

        var uri = "/api/mica/v1/entity/" + entityId.toExternalForm() + "?" + qp;
        var request = newRequest(uri)
                .GET()
                .build();

        var response = send(request, ofInputStream());
        return parseOptionalResponseAsJson(response, Entity.class);
    }

    public void deleteEntityById(EntityId entityId) {
        var uri = "/api/mica/v1/entity/" + entityId.toExternalForm();
        var request = newRequest(uri)
                .DELETE()
                .build();
        send(request, ofInputStream());
    }

    public EntityList listEntities(ListEntitiesParameters params) throws ApiException {
        var request = newRequest("/api/mica/v1/entity?" + params.toQueryParameters())
                .GET()
                .build();
        var response = send(request, ofInputStream());
        return parseResponseAsJson(response, EntityList.class);
    }

    public PartialEntity renderView(RenderViewRequest body) throws ApiException {
        var request = newRequest("/api/mica/v1/view/render")
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofByteArray(serialize(body)))
                .build();
        var response = send(request, ofInputStream());
        return parseResponseAsJson(response, PartialEntity.class);
    }

    public String renderProperties(RenderViewRequest body) throws ApiException {
        var request = newRequest("/api/mica/v1/view/renderProperties")
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofByteArray(serialize(body)))
                .build();
        var response = send(request, ofInputStream());
        return parseResponseAsText(response);
    }

    public EntityVersion uploadPartialYaml(String kind,
                                           String name,
                                           boolean replace,
                                           boolean overwrite,
                                           String updateIf,
                                           BodyPublisher bodyPublisher)
            throws ApiException {

        var qp = queryParameters(
                "entityKind", kind,
                "entityName", name,
                "replace", replace,
                "overwrite", overwrite,
                "updateIf", updateIf);

        var uri = "/api/mica/v1/upload/partialYaml?" + qp;
        var request = newRequest(uri)
                .header("Content-Type", "text/yaml")
                .PUT(bodyPublisher)
                .build();

        var response = send(request, ofInputStream());
        return parseResponseAsJson(response, EntityVersion.class);
    }

    private HttpRequest.Builder newRequest(String path) {
        var builder = HttpRequest.newBuilder()
                .header("User-Agent", userAgent)
                .timeout(requestTimeout)
                .uri(baseUri.resolve(path));
        return authorization.applyTo(builder);
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
        try {
            return client.send(request, responseBodyHandler);
        } catch (IOException e) {
            if (e instanceof HttpTimeoutException) {
                handleTimeoutException(request);
            }

            var message = e.getMessage();
            if (message == null) {
                message = e.getClass().toString();
            }
            throw new ClientException("Error sending request: " + message, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClientException("Request interrupted", e);
        }
    }

    private byte[] serialize(Object o) {
        try {
            return objectMapper.writeValueAsBytes(o);
        } catch (IOException e) {
            throw new ClientException("Error serializing object: " + e.getMessage(), e);
        }
    }

    private String parseResponseAsText(HttpResponse<InputStream> response) throws ApiException {
        try {
            handleResponseErrors(response, "Expected text response");
            assertContentType(response, "text");
            try (var responseBody = response.body()) {
                return new String(responseBody.readAllBytes(), UTF_8);
            }
        } catch (IOException e) {
            throw new ClientException("Error parsing response: " + e.getMessage(), e);
        }
    }

    private <T> T parseResponseAsJson(HttpResponse<InputStream> response,
                                      Class<T> type)
            throws ApiException {
        try {
            handleResponseErrors(response, "Expected JSON response");
            assertContentType(response, "json");
            try (var responseBody = response.body()) {
                return objectMapper.readValue(responseBody, type);
            }
        } catch (IOException e) {
            throw new ClientException("Error parsing response: " + e.getMessage(), e);
        }
    }

    private <T> Optional<T> parseOptionalResponseAsJson(HttpResponse<InputStream> response,
                                                        Class<T> type)
            throws ApiException {
        var statusCode = response.statusCode();
        if (statusCode == 404) {
            return Optional.empty();
        }
        return Optional.of(parseResponseAsJson(response, type));
    }

    private static void handleTimeoutException(HttpRequest request) {
        String destIp;
        try {
            destIp = InetAddress.getByName(request.uri().getHost()).getHostAddress();
        } catch (UnknownHostException e) {
            destIp = "unknown";
        }
        log.warn("Timeout while connecting to {} (IP: {})", request.uri(), destIp);
    }

    private static void handleResponseErrors(HttpResponse<InputStream> response, String message) throws ApiException {
        var statusCode = response.statusCode();
        if (statusCode < 200 || statusCode > 299) {
            throw ApiException.from(response, message);
        }
    }

    private static void assertContentType(HttpResponse<?> response, String expected) {
        if (response.headers().firstValue("Content-Type")
                .filter(contentType -> contentType.toLowerCase().contains(expected))
                .isEmpty()) {
            throw new RuntimeException("Not a '" + expected + "' response, status code: " + response.statusCode());
        }
    }

    private static String queryParameters(Object... kvs) {
        assert kvs != null && kvs.length % 2 == 0;
        var sb = new StringBuilder();
        for (int i = 0; i < kvs.length; i += 2) {
            assert kvs[i] != null;
            var v = kvs[i + 1];
            if (v == null || (v instanceof String s && s.isBlank())) {
                continue;
            }
            if (i > 0) {
                sb.append("&");
            }
            var k = encodeUriComponent(kvs[i].toString());
            v = encodeUriComponent(v.toString());
            sb.append(k).append('=').append(v);
        }
        return sb.toString();
    }

    private static String encodeUriComponent(String s) {
        return URLEncoder.encode(s, UTF_8);
    }

    public interface Authorization {

        HttpRequest.Builder applyTo(HttpRequest.Builder requesBuilder);
    }

    public record ApiKey(String key) implements Authorization {

        @Override
        public HttpRequest.Builder applyTo(HttpRequest.Builder requesBuilder) {
            return requesBuilder.setHeader("Authorization", key);
        }
    }

    public record SessionToken(String token) implements Authorization {

        @Override
        public HttpRequest.Builder applyTo(HttpRequest.Builder requesBuilder) {
            return requesBuilder.setHeader("X-Concord-SessionToken", token);
        }
    }

    public record ListEntitiesParameters(@Nullable String search,
            @Nullable String entityNameStartsWith,
            @Nullable String entityName,
            @Nullable String entityKind,
            @Nullable OrderBy orderBy,
            int limit) {

        public static ListEntitiesParameters byEntityName(String entityName, int limit) {
            return new ListEntitiesParameters(null, null, entityName, null, null, limit);
        }

        public String toQueryParameters() {
            return queryParameters(
                    "search", search,
                    "entityNameStartsWith", entityNameStartsWith,
                    "entityName", entityName,
                    "entityKind", entityKind,
                    "orderBy", orderBy,
                    "limit", limit);
        }
    }
}
