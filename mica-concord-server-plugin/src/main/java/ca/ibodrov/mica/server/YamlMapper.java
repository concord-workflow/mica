package ca.ibodrov.mica.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.*;

public class YamlMapper {

    private final ObjectMapper delegate;

    public YamlMapper(ObjectMapper delegate) {
        this.delegate = delegate.copyWith(YAMLFactory.builder()
                .enable(MINIMIZE_QUOTES)
                .disable(SPLIT_LINES)
                .disable(WRITE_DOC_START_MARKER)
                .enable(LITERAL_BLOCK_STYLE)
                .build());
    }

    public <T> T readValue(InputStream in, Class<T> valueType) throws IOException {
        return delegate.readValue(in, valueType);
    }

    public <T> T convertValue(Object fromValue, Class<T> valueType) {
        return delegate.convertValue(fromValue, valueType);
    }
}
