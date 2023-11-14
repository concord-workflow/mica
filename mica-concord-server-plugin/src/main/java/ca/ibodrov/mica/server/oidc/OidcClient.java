package ca.ibodrov.mica.server.oidc;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

public class OidcClient {

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final URI tokenEndpoint;
    private final URI userinfoEndpoint;
    private final String clientId;
    private final String clientSecret;

    public OidcClient(URI tokenEndpoint, URI userinfoEndpoint, String clientId, String clientSecret) {
        this.tokenEndpoint = tokenEndpoint;
        this.userinfoEndpoint = userinfoEndpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.client = HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .followRedirects(Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper()
                .registerModule(new Jdk8Module())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public CodeExchangeResponse exchangeCodeForAccessToken(String code, URI redirectUri) {
        var requestBody = "code=%s&client_id=%s&client_secret=%s&redirect_uri=%s&grant_type=authorization_code"
                .formatted(URLEncoder.encode(code, UTF_8),
                        URLEncoder.encode(clientId, UTF_8),
                        URLEncoder.encode(clientSecret, UTF_8),
                        URLEncoder.encode(redirectUri.toASCIIString(), UTF_8));

        var request = HttpRequest.newBuilder()
                .uri(tokenEndpoint)
                .header(CONTENT_TYPE, "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            return parseResponseAsJson(response, CodeExchangeResponse.class);
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public OidcUserInfo fetchUserInfo(String accessToken) {
        var request = HttpRequest.newBuilder()
                .uri(userinfoEndpoint)
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            return parseResponseAsJson(response, OidcUserInfo.class);
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T parseResponseAsJson(HttpResponse<InputStream> response, Class<T> type) throws IOException {
        if (response.headers().firstValue(CONTENT_TYPE)
                .filter(contentType -> contentType.toLowerCase().contains("json"))
                .isEmpty()) {
            throw new RuntimeException("Not a JSON response, status code: " + response.statusCode());
        }
        try (var responseBody = response.body()) {
            return objectMapper.readValue(responseBody, type);
        }
    }

    public record CodeExchangeResponse(@JsonProperty("access_token") Optional<String> accessToken,
            Optional<String> error) {
    }
}
