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

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ViewLike {

    String name();

    Selector selector();

    Data data();

    Optional<? extends Validation> validation();

    Optional<JsonNode> parameters();

    Optional<? extends Caching> caching();

    interface Selector {

        Optional<List<String>> includes();

        String entityKind();

        Optional<List<String>> namePatterns();
    }

    interface Data {

        JsonNode jsonPath();

        Optional<Boolean> flatten();

        Optional<Boolean> merge();

        Optional<JsonNode> mergeBy();

        Optional<JsonNode> jsonPatch();

        Optional<List<String>> dropProperties();

        Optional<Map<String, JsonNode>> map();

        Optional<JsonNode> template();
    }

    interface Validation {

        String asEntityKind();
    }

    interface Caching {

        Optional<String> enabled();

        Optional<String> ttl();
    }
}
