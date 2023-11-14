package ca.ibodrov.mica.concord.task;

import ca.ibodrov.mica.api.model.ClientList;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ca.ibodrov.mica.common.HttpClientUtils.parseResponseAsJson;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

@Named("mica")
public class MicaTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(MicaTask.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI baseUri;
    private final String apiKey;

    @Inject
    public MicaTask(ObjectMapper objectMapper, Context ctx) {
        this.objectMapper = requireNonNull(objectMapper);

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        this.baseUri = URI.create(ctx.apiConfiguration().baseUrl());
        this.apiKey = requireNonNull(ctx.variables().getString("apiKey"), "apiKey is null");
    }

    @Override
    public TaskResult execute(Variables input) throws Exception {
        var action = input.assertString("action");
        if (action.equalsIgnoreCase("listClients")) {
            return listClients(input);
        }
        throw new RuntimeException("Unknown 'action': " + action);
    }

    private TaskResult listClients(Variables input) throws Exception {
        var filterOutClientsWithoutProps = input.getBoolean("filterOutClientsWithoutProps", false);
        var props = input.getList("props", List.<String>of());

        log.info("Using baseUri={}", baseUri);

        var request = HttpRequest.newBuilder()
                .uri(baseUri.resolve("/api/mica/v1/client?" + toQueryParam("props", props.stream())))
                .header("Authorization", apiKey)
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        // We don't need Mica API classes to parse the response,
        // the result must be converted into regular Java structures anyway.
        // But for now we could benefit from extra validation.
        var clientList = parseResponseAsJson(objectMapper, response, ClientList.class);
        var data = filterOutClientsWithoutProps
                ? clientList.data().stream().filter(client -> client.properties().keySet().containsAll(props)).toList()
                : clientList.data();
        return TaskResult.success()
                .value("data", objectMapper.convertValue(data, List.class));
    }

    private static String toQueryParam(String key, Stream<String> stream) {
        return stream.map(prop -> key + "=" + URLEncoder.encode(prop, UTF_8))
                .collect(Collectors.joining("&"));
    }
}
