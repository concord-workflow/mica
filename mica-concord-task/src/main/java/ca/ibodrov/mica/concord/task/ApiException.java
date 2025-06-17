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

import java.io.InputStream;
import java.net.http.HttpResponse;

public class ApiException extends Exception {

    public static ApiException from(HttpResponse<InputStream> response, String message) {
        try (var body = response.body()) {
            var bytes = body.readAllBytes();
            return new ApiException(message, response.statusCode(), bytes);
        } catch (Exception e) {
            return new ApiException(message + " Failed to read response body: " + e.getMessage(), response.statusCode(),
                    new byte[0]);
        }
    }

    private final int status;

    public ApiException(String message, int code, byte[] body) {
        super(formatMessage(message, code, body));
        this.status = code;
    }

    public int getStatus() {
        return status;
    }

    private static String formatMessage(String message, int code, byte[] body) {
        var response = "[empty]";
        var maxResponseLength = Math.min(128, body.length);
        if (maxResponseLength > 0) {
            response = new String(body, 0, maxResponseLength);
            if (body.length > maxResponseLength) {
                response += "...[cut]";
            }
        }
        return "%s (code: %s). Response: %s".formatted(message, code, response);
    }
}
