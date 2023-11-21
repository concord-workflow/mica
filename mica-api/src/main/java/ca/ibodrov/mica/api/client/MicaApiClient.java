package ca.ibodrov.mica.api.client;

import ca.ibodrov.mica.api.model.SystemInfo;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static java.util.Objects.requireNonNull;

public class MicaApiClient {

    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final String apiKey;
    private final HttpClient httpClient;

    public MicaApiClient(ObjectMapper objectMapper, String baseUri, String apiKey) {
        this.objectMapper = objectMapper;
        this.baseUri = URI.create(requireNonNull(baseUri));
        this.apiKey = requireNonNull(apiKey);
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public SystemInfo getSystemInfo() {
        var request = HttpRequest.newBuilder()
                .uri(baseUri.resolve("/api/mica/v1/system"))
                .header("Authorization", apiKey)
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            return objectMapper.readValue(response.body(), SystemInfo.class);
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
