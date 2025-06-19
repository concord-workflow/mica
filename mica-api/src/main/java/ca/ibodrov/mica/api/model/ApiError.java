package ca.ibodrov.mica.api.model;

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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import javax.validation.constraints.NotEmpty;
import java.util.Optional;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_ABSENT;

/**
 * All exceptions thrown by the API should be mapped to this class.
 */
@JsonInclude(NON_ABSENT)
public record ApiError(@NotEmpty String type, @NotEmpty String message, Optional<JsonNode> payload) {

    public static ApiError notFound(String message) {
        return new ApiError("not-found", message, Optional.empty());
    }

    public static ApiError badRequest(String message) {
        return new ApiError("bad-request", message, Optional.empty());
    }

    public static ApiError conflict(String message) {
        return new ApiError("conflict", message, Optional.empty());
    }

    public static ApiError internalError(String message) {
        return new ApiError("internal-error", message, Optional.empty());
    }

    public static ApiError unauthorized(String message) {
        return new ApiError("unauthorized", message, Optional.empty());
    }
}
