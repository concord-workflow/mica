package ca.ibodrov.mica.server.data;

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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.networknt.schema.ValidationMessage;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorXO;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;

import java.util.Set;
import java.util.UUID;

import static java.util.Comparator.comparing;

public record ValidatedInput(Set<ValidationMessage> messages) {

    @JsonIgnore
    public boolean isValid() {
        return messages.isEmpty();
    }

    @JsonIgnore
    public ValidationErrorsException toException() {
        if (isValid()) {
            throw new IllegalStateException("Input is valid");
        }

        // TODO use path, combine messages for each path
        return new ValidationErrorsException()
                .withErrors(messages.stream()
                        .sorted(comparing(ValidationMessage::getPath).thenComparing(ValidationMessage::getMessage))
                        .map(m -> new ValidationErrorXO(UUID.randomUUID().toString(), m.getMessage()))
                        .toList());
    }
}
