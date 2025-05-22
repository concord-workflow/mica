package ca.ibodrov.mica.server.exceptions;

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

import ca.ibodrov.mica.api.model.ApiError;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.*;

public class ApiException extends WebApplicationException {

    public static ApiException badRequest(String message) {
        return new ApiException(BAD_REQUEST, ApiError.badRequest(message));
    }

    public static ApiException notFound(String message) {
        return new ApiException(NOT_FOUND, ApiError.notFound(message));
    }

    public static ApiException internalError(String message) {
        return new ApiException(INTERNAL_SERVER_ERROR, ApiError.internalError(message));
    }

    public static ApiException conflict(String message) {
        return new ApiException(CONFLICT, ApiError.conflict(message));
    }

    public ApiException(Status status, ApiError error) {
        this(error.message(), null, status, error);
    }

    public ApiException(String message, Throwable cause, Status status, ApiError error) {
        super(message, cause, Response.status(status)
                .type(APPLICATION_JSON)
                .entity(error)
                .build());
    }

    public Status getStatus() {
        return Status.fromStatusCode(getResponse().getStatus());
    }
}
