package ca.ibodrov.mica.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.Reader;

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.*;
import static java.util.Objects.requireNonNull;

public class YamlMapper {

    private final ObjectMapper delegate;
    private final ObjectWriter prettyPrinter;

    public YamlMapper(ObjectMapper delegate) {
        this.delegate = requireNonNull(delegate).copyWith(YAMLFactory.builder()
                .disable(MINIMIZE_QUOTES)
                .disable(SPLIT_LINES)
                .disable(WRITE_DOC_START_MARKER)
                .enable(LITERAL_BLOCK_STYLE)
                .build());

        this.prettyPrinter = this.delegate.writerWithDefaultPrettyPrinter();
    }

    public <T> T readValue(String src, Class<T> valueType) throws JsonProcessingException {
        return delegate.readValue(src, valueType);
    }

    public <T> T readValue(Reader src, Class<T> valueType) throws IOException {
        return delegate.readValue(src, valueType);
    }

    public <T> T convertValue(Object fromValue, Class<T> valueType) {
        return delegate.convertValue(fromValue, valueType);
    }

    public String prettyPrint(Object value) throws IOException {
        return prettyPrinter.writeValueAsString(value);
    }

    public ObjectNode createObjectNode() {
        return delegate.createObjectNode();
    }
}
