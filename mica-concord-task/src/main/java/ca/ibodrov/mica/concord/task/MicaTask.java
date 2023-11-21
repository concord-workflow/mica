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
        log.info("Using baseUri={}", baseUri);

        var action = input.assertString("action");
        if (action.equalsIgnoreCase("listClients")) {
            return listClients(input);
        }
        throw new RuntimeException("Unknown 'action': " + action);
    }

    private TaskResult listClients(Variables input) throws Exception {
        var filterOutClientsWithoutProps = input.getBoolean("filterOutClientsWithoutProps", false);
        var props = assertListOfStrings(input, "props");

        var request = newRequest("/api/mica/v1/client?" + toQueryParam("props", props.stream()))
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        // We don't need Mica API classes to parse the response,
        // the result must be converted into regular Java structures anyway.
        // But for now we use them to validate the response to be sure that the client
        // is compatible.
        var clientList = parseResponseAsJson(objectMapper, response, ClientList.class);
        var data = filterOutClientsWithoutProps
                ? clientList.data().stream().filter(client -> client.properties().keySet().containsAll(props)).toList()
                : clientList.data();
        return TaskResult.success()
                .value("data", objectMapper.convertValue(data, List.class));
    }

    private HttpRequest.Builder newRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(baseUri.resolve(path))
                .header("X-Concord-SessionToken", sessionToken);
    }

    private static List<String> assertListOfStrings(Variables input, String key) {
        var raw = input.getList(key, List.of());
        return raw.stream().map(o -> {
            if (!(o instanceof String)) {
                throw new RuntimeException("Expected a list of strings, got: " + raw);
            }
            return o.toString();
        }).toList();
    }

    private static String toQueryParam(String key, Stream<String> stream) {
        return stream.map(prop -> key + "=" + URLEncoder.encode(prop, UTF_8))
                .collect(Collectors.joining("&"));
    }
}
