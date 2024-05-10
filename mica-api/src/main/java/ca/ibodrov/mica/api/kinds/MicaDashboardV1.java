package ca.ibodrov.mica.api.kinds;

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
