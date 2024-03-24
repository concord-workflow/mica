package ca.ibodrov.mica.server.data.meta;

import java.util.List;

record OperationResult<T, W>(List<T> items, List<W> warnings) {

    @SafeVarargs
    public static <T, W> OperationResult<T, W> items(T... items) {
        return new OperationResult<>(List.of(items), List.of());
    }

    @SafeVarargs
    public static <T, W> OperationResult<T, W> warnings(W... warnings) {
        return new OperationResult<>(List.of(), List.of(warnings));
    }
}
