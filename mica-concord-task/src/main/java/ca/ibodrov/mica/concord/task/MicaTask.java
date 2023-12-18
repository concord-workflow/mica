package ca.ibodrov.mica.concord.task;

import ca.ibodrov.mica.api.model.EntityList;
import ca.ibodrov.mica.api.model.PartialEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static java.net.http.HttpClient.Redirect.NEVER;
import static java.net.http.HttpRequest.BodyPublishers.ofFile;
import static java.net.http.HttpRequest.BodyPublishers.ofString;
import static java.net.http.HttpResponse.BodyHandlers.ofInputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

@Named("mica")
public class MicaTask implements Task {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI baseUri;
    private final String sessionToken;

    @Inject
    public MicaTask(ObjectMapper objectMapper, Context ctx) {
        this.objectMapper = requireNonNull(objectMapper);

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        this.baseUri = URI.create(ctx.apiConfiguration().baseUrl());
        this.sessionToken = requireNonNull(ctx.processConfiguration().processInfo().sessionToken(),
                "sessionToken is null");
    }

    @Override
    public TaskResult execute(Variables input) throws Exception {
        var action = input.assertString("action");
        if (action.equalsIgnoreCase("listEntities")) {
            return listEntities(input);
        } else if (action.equalsIgnoreCase("upload")) {
            return upload(input);
        } else if (action.equalsIgnoreCase("renderView")) {
            return renderView(input);
        }
        throw new RuntimeException("Unknown 'action': " + action);
    }

    private TaskResult listEntities(Variables input) throws Exception {
        var search = input.getString("search", "");
        var request = newRequest("/api/mica/v1/entity?search=" + URLEncoder.encode(search, UTF_8)).build();
        var response = httpClient.send(request, ofInputStream());
        var entityList = parseResponseAsJson(objectMapper, response, EntityList.class);
        // TODO figure out when date-time becomes a timestamp
        return TaskResult.success()
                .value("data", objectMapper.convertValue(entityList.data(), List.class));
    }

    private TaskResult upload(Variables input) throws Exception {
        var kind = input.assertString("kind");
        var src = input.assertString("src");
        var name = input.assertString("name");
        var uri = "/api/mica/v1/upload/partialYaml?entityKind=" + encodeUriComponent(kind) + "&entityName="
                + encodeUriComponent(name) + "&replace=true";
        var request = newRequest(uri)
                .header("Content-Type", "text/yaml") // TODO allow .json
                .PUT(ofFile(Path.of(src)))
                .build();
        var response = httpClient.send(request, ofInputStream());
        return TaskResult.success()
                .value("version", parseResponseAsJson(objectMapper, response, Map.class));
    }

    private TaskResult renderView(Variables input) throws Exception {
        var view = input.assertString("name");
        var parameters = input.getMap("parameters", Map.of());
        var limit = input.getNumber("limit", -1);
        var request = newRequest("/api/mica/v1/view/render")
                .header("Content-Type", "application/json")
                .POST(ofString(objectMapper.writeValueAsString(Map.of(
                        "viewName", view,
                        "parameters", parameters,
                        "limit", limit))))
                .build();
        var response = httpClient.send(request, ofInputStream());
        var rendered = parseResponseAsJson(objectMapper, response, PartialEntity.class);
        return TaskResult.success()
                .value("data", objectMapper.convertValue(rendered.data().get("data"), List.class));
    }

    private HttpRequest.Builder newRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(baseUri.resolve(path))
                .header("X-Concord-SessionToken", sessionToken);
    }

    private static <T> T parseResponseAsJson(ObjectMapper objectMapper,
                                             HttpResponse<InputStream> response,
                                             Class<T> type)
            throws IOException {
        var statusCode = response.statusCode();
        if (statusCode < 200 || statusCode > 299) {
            try (var body = response.body()) {
                throw new RuntimeException(
                        "Request error: " + response.statusCode() + " " + new String(body.readAllBytes(), UTF_8));
            }
        }
        if (response.headers().firstValue("Content-Type")
                .filter(contentType -> contentType.toLowerCase().contains("json"))
                .isEmpty()) {
            throw new RuntimeException("Not a JSON response, status code: " + response.statusCode());
        }
        try (var responseBody = response.body()) {
            return objectMapper.readValue(responseBody, type);
        }
    }

    private static String encodeUriComponent(String s) {
        return URLEncoder.encode(s, UTF_8);
    }
}
