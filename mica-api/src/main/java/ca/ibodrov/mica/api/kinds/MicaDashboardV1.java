package ca.ibodrov.mica.api.kinds;

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
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record MicaDashboardV1(String title,
        ViewRef view,
        Layout layout,
        Optional<TableLayout> table) {

    public static final String MICA_DASHBOARD_V1 = "/mica/dashboard/v1";

    public MicaDashboardV1 {
        requireNonNull(title, "missing 'title'");
        requireNonNull(view, "missing 'view'");
        requireNonNull(layout, "missing 'layout'");
        requireNonNull(table, "missing 'table'");
    }

    public enum Layout {
        TABLE
    }

    public record ViewRef(@ValidName String name, Optional<JsonNode> parameters) {
    }

    public record TableLayout(List<TableColumnDef> columns) {
    }

    public record TableColumnDef(String title, String jsonPath) {
    }
}
