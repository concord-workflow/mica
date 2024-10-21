package ca.ibodrov.mica.concord.task;

import java.io.InputStream;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;

public class ApiException extends Exception {

    public static ApiException from(HttpResponse<InputStream> response, String message) {
        try (var body = response.body()) {
            byte[] bytes = body.readAllBytes();
            return new ApiException(message, response.statusCode(), response.headers(), bytes);
        } catch (Exception e) {
            return new ApiException(message + " Failed to read response body: " + e.getMessage(), response.statusCode(),
                    response.headers(), new byte[0]);
        }
    }

    private final int status;
    private final HttpHeaders headers;
    private final byte[] body;

    public ApiException(String message, int code, HttpHeaders headers, byte[] body) {
        super(message);
        this.status = code;
        this.headers = headers;
        this.body = body;
    }

    public int getStatus() {
        return status;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }
}
