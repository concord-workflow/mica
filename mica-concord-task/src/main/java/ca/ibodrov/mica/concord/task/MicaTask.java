package ca.ibodrov.mica.concord.task;

import ca.ibodrov.mica.api.model.*;
import ca.ibodrov.mica.concord.task.MicaClient.ApiKey;
import ca.ibodrov.mica.concord.task.MicaClient.Authorization;
import ca.ibodrov.mica.concord.task.MicaClient.ListEntitiesParameters;
import ca.ibodrov.mica.concord.task.MicaClient.SessionToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import com.walmartlabs.concord.sdk.MapUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.net.http.HttpClient.Redirect.NEVER;
import static java.net.http.HttpRequest.BodyPublishers.ofFile;
import static java.util.Objects.requireNonNull;

@Named("mica")
public class MicaTask implements Task {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI defaultBaseUri;
    private final Optional<String> sessionToken;

    private final Map<String, Object> defaultVariables;

    @Inject
    public MicaTask(ObjectMapper objectMapper, Context ctx) {
        this.objectMapper = requireNonNull(objectMapper);

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        this.defaultBaseUri = URI.create(ctx.apiConfiguration().baseUrl());
        this.sessionToken = Optional.ofNullable(ctx.processConfiguration().processInfo().sessionToken());
        this.defaultVariables = ctx.defaultVariables().toMap();
    }

    @Override
    public TaskResult execute(Variables input) throws Exception {
        var action = input.assertString("action");
        return switch (action) {
            case "batch" -> batchAction(input);
            case "listEntities" -> listEntities(input);
            case "renderView" -> renderView(input);
            case "renderProperties" -> renderProperties(input);
            case "upload" -> upload(input);
            case "upsert" -> upsert(input);
            default -> throw new RuntimeException("Unknown 'action': " + action);
        };
    }

    private TaskResult batchAction(Variables input) {
        var operation = BatchOperation.valueOf(input.assertString("operation").toUpperCase());
        var namePatterns = Optional.of(input.<String>assertList("namePatterns"));

        var response = new MicaClient(httpClient, baseUri(input), auth(input), objectMapper)
                .apply(new BatchOperationRequest(operation, namePatterns));

        return TaskResult.success()
                .values(objectMapper.convertValue(response, Map.class));
    }

    private TaskResult listEntities(Variables input) {
        var params = new ListEntitiesParameters(
                input.getString("search"),
                input.getString("entityNameStartsWith"),
                input.getString("entityName"),
                input.getString("entityKind"),
                Optional.ofNullable(input.getString("orderBy"))
                        .filter(s -> !s.isBlank())
                        .map(OrderBy::valueOf)
                        .orElse(null),
                input.getInt("limit", -1));

        var entityList = new MicaClient(httpClient, baseUri(input), auth(input), objectMapper).listEntities(params);

        return TaskResult.success()
                .value("data", objectMapper.convertValue(entityList.data(), List.class));
    }

    private RenderRequest parseRenderRequest(Variables input) {
        var viewName = input.assertString("name");
        var parameters = parseParameters(input);
        var limit = input.getInt("limit", -1);
        return new RenderRequest(Optional.empty(), Optional.of(viewName), limit,
                Optional.of(objectMapper.convertValue(parameters, JsonNode.class)));
    }

    private TaskResult renderView(Variables input) {
        var body = parseRenderRequest(input);
        var rendered = new MicaClient(httpClient, baseUri(input), auth(input), objectMapper)
                .renderView(body);
        return TaskResult.success()
                .value("data", objectMapper.convertValue(rendered.data().get("data"), List.class));
    }

    private TaskResult renderProperties(Variables input) {
        var body = parseRenderRequest(input);
        var rendered = new MicaClient(httpClient, baseUri(input), auth(input), objectMapper)
                .renderProperties(body);
        return TaskResult.success().value("data", rendered);
    }

    private TaskResult upload(Variables input) throws FileNotFoundException {
        var kind = input.assertString("kind");
        var src = input.assertString("src");
        var name = input.assertString("name");
        var response = new MicaClient(httpClient, baseUri(input), auth(input), objectMapper)
                .uploadPartialYaml(kind, name, true, ofFile(Path.of(src)));
        return TaskResult.success()
                .value("version", objectMapper.convertValue(response, Map.class));
    }

    private TaskResult upsert(Variables input) {
        var client = new MicaClient(httpClient, baseUri(input), auth(input), objectMapper);

        var kind = input.getString("kind");
        var name = input.assertString("name");
        var body = input.assertVariable("entity", Object.class);
        var merge = input.getBoolean("merge", false);

        if (merge) {
            var meta = findEntityByName(input, name);
            if (meta.isPresent()) {
                var existingVersion = meta.get();
                var entity = client.getEntityById(existingVersion.id(), existingVersion.updatedAt())
                        .orElseThrow(() -> new RuntimeException("Conflict: " + name));

                var a = objectMapper.convertValue(entity, Map.class);
                var b = objectMapper.convertValue(body, Map.class);
                body = ConfigurationUtils.deepMerge(a, b);
            }
        }

        byte[] requestBody;
        try {
            requestBody = objectMapper.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new RuntimeException("Error serializing entity: " + name, e);
        }

        var updatedVersion = client.uploadPartialYaml(kind, name, false, BodyPublishers.ofByteArray(requestBody));
        return TaskResult.success()
                .value("version", objectMapper.convertValue(updatedVersion, Map.class));
    }

    private Optional<EntityMetadata> findEntityByName(Variables input, String name) {
        var existingEntities = new MicaClient(httpClient, baseUri(input), auth(input), objectMapper)
                .listEntities(ListEntitiesParameters.byEntityName(name, 2));

        if (existingEntities.data().isEmpty()) {
            return Optional.empty();
        }
        if (existingEntities.data().size() > 1) {
            throw new RuntimeException("Multiple entities found: " + name);
        }

        return Optional.of(existingEntities.data().get(0));
    }

    private URI baseUri(Variables input) {
        var baseUrl = input.getString("baseUrl", MapUtils.getString(defaultVariables, "baseUrl"));
        if (baseUrl != null) {
            return URI.create(baseUrl);
        }
        return defaultBaseUri;
    }

    private Authorization auth(Variables input) {
        var apiKey = input.getString("apiKey", MapUtils.getString(defaultVariables, "apiKey"));
        if (apiKey != null) {
            return new ApiKey(apiKey);
        }

        return new SessionToken(sessionToken.orElseThrow(() -> new IllegalArgumentException(
                "Authentication error: no session token found and no 'apiKey' provided")));
    }

    private static Map<String, Object> parseParameters(Variables input) {
        Map<String, Object> params = input.getMap("parameters", Map.of());
        // remove all "null" values
        params.values().removeIf(Objects::isNull);
        return params;
    }
}
