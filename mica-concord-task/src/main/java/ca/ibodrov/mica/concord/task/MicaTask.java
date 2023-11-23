package ca.ibodrov.mica.concord.task;

import ca.ibodrov.mica.api.model.EntityList;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import javax.inject.Inject;
import javax.inject.Named;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static ca.ibodrov.mica.common.HttpClientUtils.parseResponseAsJson;
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
                .followRedirects(HttpClient.Redirect.NEVER)
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
        }
        throw new RuntimeException("Unknown 'action': " + action);
    }

    private TaskResult listEntities(Variables input) throws Exception {
        var search = input.getString("search", "");
        var request = newRequest("/api/mica/v1/entity?search=" + URLEncoder.encode(search, UTF_8)).build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        var entityList = parseResponseAsJson(objectMapper, response, EntityList.class);
        // TODO figure out when date-time becomes a timestamp
        return TaskResult.success()
                .value("data", objectMapper.convertValue(entityList.data(), List.class));
    }

    private HttpRequest.Builder newRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(baseUri.resolve(path))
                .header("X-Concord-SessionToken", sessionToken);
    }
}
