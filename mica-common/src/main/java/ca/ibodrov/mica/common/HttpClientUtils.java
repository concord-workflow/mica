package ca.ibodrov.mica.common;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

public final class HttpClientUtils {

    public static <T> T parseResponseAsJson(ObjectMapper objectMapper,
                                            HttpResponse<InputStream> response,
                                            Class<T> type)
            throws IOException {
        if (response.headers().firstValue(CONTENT_TYPE)
                .filter(contentType -> contentType.toLowerCase().contains("json"))
                .isEmpty()) {
            throw new RuntimeException("Not a JSON response, status code: " + response.statusCode());
        }
        try (var responseBody = response.body()) {
            return objectMapper.readValue(responseBody, type);
        }
    }

    private HttpClientUtils() {
    }
}
