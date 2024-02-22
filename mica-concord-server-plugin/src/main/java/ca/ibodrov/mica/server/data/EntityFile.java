package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.YamlMapper;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;

record EntityFile(FileFormat format, Path path) {

    static final String PROPERTIES_KIND = "/mica/java-properties/v1";

    EntityLike parseAsEntity(YamlMapper yamlMapper, Path rootPath) {
        try (var reader = Files.newBufferedReader(this.path(), UTF_8)) {
            switch (this.format()) {
                case YAML -> {
                    return yamlMapper.readValue(reader, PartialEntity.class);
                }
                case PROPERTIES -> {
                    var props = new Properties();
                    props.load(reader);
                    return parseProperties(yamlMapper, rootPath, this, props);
                }
            }
            return yamlMapper.readValue(reader, PartialEntity.class);
        } catch (IOException e) {
            throw new StoreException("Error while reading %s: %s".formatted(this.path(), e.getMessage()), e);
        } catch (RuntimeException e) {
            // TODO something better
            if (e.getCause() instanceof JsonProcessingException) {
                throw new StoreException(
                        "Error while parsing %s: %s".formatted(this.path(), e.getCause().getMessage()),
                        e.getCause());
            }
            throw e;
        }
    }

    private static EntityLike parseProperties(YamlMapper yamlMapper,
                                              Path rootPath,
                                              EntityFile entityFile,
                                              Properties props)
            throws IOException {

        var node = yamlMapper.createObjectNode();
        props.forEach((k, v) -> node.set((String) k, TextNode.valueOf((String) v)));

        var data = ImmutableMap.<String, JsonNode>builder();
        node.fields().forEachRemaining(e -> data.put(e.getKey(), e.getValue()));

        var name = rootPath.relativize(entityFile.path()).toString();
        return PartialEntity.create(name, PROPERTIES_KIND, data.build());
    }
}
