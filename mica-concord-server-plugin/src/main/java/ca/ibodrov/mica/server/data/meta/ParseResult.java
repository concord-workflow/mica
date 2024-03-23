package ca.ibodrov.mica.server.data.meta;

import java.util.List;

record ParseResult<T, W>(List<T> items, List<W> warnings) {

    public static <T, W> ParseResult<T, W> items(T... items) {
        return new ParseResult<>(List.of(items), List.of());
    }

    public static <T, W> ParseResult<T, W> warnings(W... warnings) {
        return new ParseResult<>(List.of(), List.of(warnings));
    }
}
