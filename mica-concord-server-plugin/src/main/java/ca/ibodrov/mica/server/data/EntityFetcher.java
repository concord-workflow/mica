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

import ca.ibodrov.mica.api.model.EntityLike;

import java.net.URI;
import java.util.Optional;
import java.util.stream.Stream;

public interface EntityFetcher {

    default Optional<URI> defaultUri() {
        return Optional.empty();
    }

    boolean isSupported(FetchRequest request);

    Cursor fetch(FetchRequest request);

    record FetchRequest(Optional<URI> uri, Optional<String> kind) {

        public static FetchRequest ofUri(URI uri) {
            return new FetchRequest(Optional.of(uri), Optional.empty());
        }

        public static FetchRequest ofKind(String kind) {
            return new FetchRequest(Optional.empty(), Optional.of(kind));
        }
    }

    interface Cursor {

        Stream<EntityLike> stream();
    }
}
