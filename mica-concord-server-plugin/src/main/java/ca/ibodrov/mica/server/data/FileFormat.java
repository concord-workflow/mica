package ca.ibodrov.mica.server.data;

import java.util.stream.Stream;

enum FileFormat {
    YAML,
    PROPERTIES;

    static Stream<FileFormat> stream() {
        return Stream.of(values());
    }
}
