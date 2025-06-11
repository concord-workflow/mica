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

import ca.ibodrov.mica.api.validation.ValidName;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import javax.validation.ConstraintViolationException;
import java.util.Optional;
import java.util.Set;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_ABSENT;
import static java.util.Objects.requireNonNull;

@JsonInclude(NON_ABSENT)
public record RenderRequest(Optional<EntityId> viewId,
        Optional<String> viewName,
        Optional<JsonNode> parameters) {

    public RenderRequest {
        requireNonNull(viewId);
        requireNonNull(viewName);

        if (viewId.isEmpty() && viewName.isEmpty()) {
            throw new ConstraintViolationException("One of viewId or viewName must be set", Set.of());
        }

        viewName.ifPresent(name -> {
            if (!name.matches(ValidName.NAME_PATTERN)) {
                throw new ConstraintViolationException("Invalid view name: " + name + ". " + ValidName.MESSAGE,
                        Set.of());
            }
        });
    }

    public static RenderRequest of(String viewName) {
        return new RenderRequest(Optional.empty(), Optional.of(viewName), Optional.empty());
    }

    public static RenderRequest parameterized(String viewName, JsonNode parameters) {
        return new RenderRequest(Optional.empty(), Optional.of(viewName), Optional.ofNullable(parameters));
    }
}
