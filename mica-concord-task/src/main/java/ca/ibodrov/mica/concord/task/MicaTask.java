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
import ca.ibodrov.mica.concord.task.MicaClient.ApiKey;
import ca.ibodrov.mica.concord.task.MicaClient.Authorization;
import ca.ibodrov.mica.concord.task.MicaClient.ListEntitiesParameters;
import ca.ibodrov.mica.concord.task.MicaClient.SessionToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.sdk.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import static ca.ibodrov.mica.concord.task.MicaClient.ListEntitiesParameters.byEntityName;
import static ca.ibodrov.mica.concord.task.Retry.withRetry;
import static java.net.http.HttpClient.Redirect.NEVER;
import static java.net.http.HttpRequest.BodyPublishers.ofString;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

@Named("mica")
@DryRunReady
public class MicaTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(MicaTask.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI defaultBaseUri;
    private final Optional<String> sessionToken;
    private final String userAgent;

    private final Map<String, Object> defaultVariables;
    private final boolean dryRun;
    private final Path workDir;

    @Inject
    public MicaTask(ObjectMapper objectMapper, Context ctx) {
        this.objectMapper = requireNonNull(objectMapper).copy()
                .registerModule(new JavaTimeModule())
                .registerModule(new Jdk8Module())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        this.defaultBaseUri = URI.create(ctx.apiConfiguration().baseUrl());
        this.sessionToken = Optional.ofNullable(ctx.processConfiguration().processInfo().sessionToken());
        this.defaultVariables = ctx.defaultVariables().toMap();

        try (var in = MicaTask.class.getResourceAsStream("/ca/ibodrov/mica/concord/task/version.properties")) {
            var props = new Properties();
            props.load(in);
            var version = Optional.ofNullable(props.getProperty("version")).orElseThrow();
            this.userAgent = "mica (version=%s, instanceId=%s)".formatted(version, ctx.processInstanceId());
        } catch (IOException e) {
            throw new RuntimeException("Can't load version.properties", e);
        }

        this.dryRun = ctx.processConfiguration().dryRun();
        this.workDir = ctx.workingDirectory();
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
            case "delete" -> delete(input);
            default -> throw new RuntimeException("Unknown 'action': " + action);
        };
    }

    private TaskResult batchAction(Variables input) throws ApiException {
        var operation = BatchOperation.valueOf(input.assertString("operation").toUpperCase());
        if (isDryRun(input)) {
            log.info("Dry-run mode enabled: Skipping batch '{}'", operation);
            return TaskResult.success()
                    .value("deletedEntities", List.of());
        }

        var namePatterns = Optional.of(input.<String>assertList("namePatterns"));

        var client = createMicaClient(input);
        var response = withRetry(log, () -> client.apply(new BatchOperationRequest(operation, namePatterns)));

        return TaskResult.success()
                .values(objectMapper.convertValue(response, Map.class));
    }

    private TaskResult listEntities(Variables input) throws ApiException {
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

        var client = createMicaClient(input);
        var entityList = withRetry(log, () -> client.listEntities(params));

        return TaskResult.success()
                .value("data", objectMapper.convertValue(entityList.data(), List.class));
    }

    private RenderViewRequest parseRenderRequest(Variables input) {
        var viewName = input.assertString("name");
        var parameters = parseParameters(input);
        return new RenderViewRequest(Optional.empty(), Optional.of(viewName),
                Optional.of(objectMapper.convertValue(parameters, JsonNode.class)));
    }

    private TaskResult renderView(Variables input) throws ApiException {
        var body = parseRenderRequest(input);
        var client = createMicaClient(input);
        var rendered = withRetry(log, () -> client.renderView(body));
        return TaskResult.success()
                .value("data", objectMapper.convertValue(rendered.data().get("data"), List.class));
    }

    private TaskResult renderProperties(Variables input) throws ApiException {
        var body = parseRenderRequest(input);
        var client = createMicaClient(input);
        var rendered = withRetry(log, () -> client.renderProperties(body));
        return TaskResult.success().value("data", rendered);
    }

    private TaskResult upload(Variables input) throws IOException, ApiException {
        var src = Path.of(input.assertString("src")).toAbsolutePath().normalize();
        if (!src.startsWith(workDir)) {
            throw new IllegalArgumentException("The 'src' path must be within ${workDir}");
        }

        var kind = input.getString("kind");
        var name = input.getString("name");
        var replace = input.getBoolean("replace", true);
        var overwrite = input.getBoolean("overwrite", true);
        var updateIf = input.getString("updateIf");

        var body = hideSensitiveData(input, Files.readString(src));

        if (isDryRun(input)) {
            log.info("Dry-run mode enabled: Skipping upload and returning a random 'version' value");
            return TaskResult.success()
                    .value("version", Map.of(
                            "id", UUID.randomUUID().toString(),
                            "updatedAt", "2023-01-01T00:00:00Z"));
        }

        var client = createMicaClient(input);
        var response = withRetry(log,
                () -> client.uploadPartialYaml(kind, name, replace, overwrite, updateIf, ofString(body)));
        return TaskResult.success()
                .value("version", objectMapper.convertValue(response, Map.class));
    }

    private TaskResult upsert(Variables input) throws ApiException {
        var client = createMicaClient(input);

        var kind = input.getString("kind");
        var name = input.assertString("name");
        var body = input.assertVariable("entity", Object.class);
        var merge = input.getBoolean("merge", false);
        var updateIf = input.getString("updateIf");

        if (merge) {
            var meta = findEntityByName(input, name);
            if (meta.isPresent()) {
                var existingVersion = meta.get();
                var entity = withRetry(log,
                        () -> client.getEntityById(existingVersion.id(), existingVersion.updatedAt()))
                        .orElseThrow(() -> new RuntimeException("Conflict: " + name));

                var a = objectMapper.convertValue(entity, Map.class);
                var b = objectMapper.convertValue(body, Map.class);
                // noinspection unchecked
                body = ConfigurationUtils.deepMerge(a, b);
            }
        }

        body = hideSensitiveData(input, body);

        if (isDryRun(input)) {
            var existingVersion = findEntityByName(input, name)
                    .map(EntityMetadata::toVersion)
                    .orElse(null);

            if (existingVersion == null) {
                log.info(
                        "Dry-run mode enabled: Skipping upsert and returning a random 'version' value (no existing entity found)");
                return TaskResult.success()
                        .value("version", Map.of(
                                "id", UUID.randomUUID().toString(),
                                "updatedAt", "2023-01-01T00:00:00Z"));
            }

            log.info("Dry-run mode enabled: Skipping upsert and returning the entity's existing version");
            return TaskResult.success()
                    .value("version", objectMapper.convertValue(existingVersion, Map.class));
        }

        byte[] requestBody;
        try {
            requestBody = objectMapper.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new RuntimeException("Error serializing entity: " + name, e);
        }

        var updatedVersion = client.uploadPartialYaml(kind, name, false, false, updateIf,
                BodyPublishers.ofByteArray(requestBody));
        return TaskResult.success()
                .value("version", objectMapper.convertValue(updatedVersion, Map.class));
    }

    private TaskResult delete(Variables input) throws ApiException {
        var name = input.assertString("name");

        var meta = findEntityByName(input, name);
        if (meta.isEmpty()) {
            return TaskResult.success();
        }

        if (isDryRun(input)) {
            log.info("Dry-run mode enabled: Skipping delete action");

            return TaskResult.success();
        }

        var client = createMicaClient(input);
        client.deleteEntityById(meta.get().id());

        return TaskResult.success();
    }

    private Optional<EntityMetadata> findEntityByName(Variables input, String name) throws ApiException {
        var existingEntities = createMicaClient(input).listEntities(byEntityName(name, 2));

        if (existingEntities.data().isEmpty()) {
            return Optional.empty();
        }
        if (existingEntities.data().size() > 1) {
            throw new RuntimeException("Multiple entities found: " + name);
        }

        return Optional.of(existingEntities.data().get(0));
    }

    private MicaClient createMicaClient(Variables input) {
        return new MicaClient(httpClient, baseUri(input), auth(input), userAgent, objectMapper);
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

    private boolean isDryRun(Variables input) {
        return input.getBoolean("dryRun", this.dryRun);
    }

    private static Map<String, Object> parseParameters(Variables input) {
        Map<String, Object> params = input.getMap("parameters", Map.of());
        // remove all "null" values
        params.values().removeIf(Objects::isNull);
        return params;
    }

    private static <T> T hideSensitiveData(Variables input, T body) {
        var hideSensitiveData = input.getBoolean("hideSensitiveData", true);
        if (hideSensitiveData) {
            var sensitiveDataExclusions = input.getList("sensitiveDataExclusions", List.of())
                    .stream()
                    .map(Object::toString)
                    .collect(toSet());
            return SensitiveDataUtils.hideSensitiveData(body, sensitiveDataExclusions);
        }
        return body;
    }
}
