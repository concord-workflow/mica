package ca.ibodrov.mica.server.data;

import java.nio.file.Path;
import java.util.stream.Stream;

enum FileFormat {
    YAML {
        @Override
        boolean matches(Path path) {
            var fileName = path.getFileName().toString();
            return fileName.endsWith(".yaml") || fileName.endsWith(".yml");
        }

    },
    PROPERTIES {
        @Override
        boolean matches(Path path) {
            return path.getFileName().toString().endsWith(".properties");
        }
    };

    abstract boolean matches(Path path);

    static Stream<FileFormat> stream() {
        return Stream.of(values());
    }
}
