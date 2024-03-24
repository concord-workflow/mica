package ca.ibodrov.mica.server.data.meta;

import javax.annotation.Nullable;

public record ScannerWarning(String message, int lineNum, @Nullable String line) {
}
