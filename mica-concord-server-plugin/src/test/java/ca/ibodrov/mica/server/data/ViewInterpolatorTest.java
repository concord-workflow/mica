package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.kinds.MicaViewV1;
import ca.ibodrov.mica.server.data.Validator.NoopSchemaFetcher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static ca.ibodrov.mica.api.kinds.MicaViewV1.Data.jsonPath;
import static ca.ibodrov.mica.api.kinds.MicaViewV1.Selector.byEntityKind;
import static ca.ibodrov.mica.api.kinds.MicaViewV1.Validation.asEntityKind;
import static org.junit.Assert.*;

public class ViewInterpolatorTest {

    private static final ObjectMapper objectMapper = new ObjectMapperProvider().get();
    private static final ViewInterpolator interpolator = new ViewInterpolator(objectMapper, new NoopSchemaFetcher());

    @Test
    public void unresolvedPlaceholdersMustBeEscaped() {
        var view = new MicaViewV1.Builder()
                .name("test")
                .selector(byEntityKind("${parameters.kind}")
                        .withIncludes(List.of("${parameters.include}"))
                        .withNamePatterns(List.of("${parameters.name}")))
                .data(jsonPath("${parameters.jsonPath}"))
                .validation(asEntityKind("${parameters.validationKind}"))
                .parameters(parseObject("""
                        properties:
                            kind:
                                type: string
                            include:
                                type: string
                            name:
                                type: string
                            jsonPath:
                                type: string
                            validationKind:
                                type: string
                        """))
                .build();

        var interpolatedView = interpolator.interpolate(view, null);
        assertEquals("\\$\\{parameters.kind}", interpolatedView.selector().entityKind());
        assertEquals("\\$\\{parameters.include}", interpolatedView.selector().includes().get().get(0));
        assertEquals("\\$\\{parameters.name}", interpolatedView.selector().namePatterns().get().get(0));
        assertEquals("\\$\\{parameters.jsonPath}", interpolatedView.data().jsonPath());
        assertEquals("\\$\\{parameters.validationKind}", interpolatedView.validation().get().asEntityKind());
    }

    @Test
    public void placeholdersAreInterpolated() {
        var view = new MicaViewV1.Builder()
                .name("test")
                .selector(byEntityKind("${parameters.kind}")
                        .withIncludes(List.of("${parameters.include}"))
                        .withNamePatterns(List.of("${parameters.name}")))
                .data(jsonPath("${parameters.jsonPath}"))
                .validation(asEntityKind("${parameters.validationKind}"))
                .parameters(parseObject("""
                        properties:
                            kind:
                                type: string
                            include:
                                type: string
                            name:
                                type: string
                            jsonPath:
                                type: string
                            validationKind:
                                type: string
                        """))
                .build();

        var interpolatedView = interpolator.interpolate(view,
                objectMapper.convertValue(Map.of(
                        "kind", "kind1",
                        "include", "include1",
                        "name", "name1",
                        "jsonPath", "jsonPath1",
                        "validationKind", "validationKind1"), JsonNode.class));

        assertEquals("kind1", interpolatedView.selector().entityKind());
        assertEquals("include1", interpolatedView.selector().includes().get().get(0));
        assertEquals("name1", interpolatedView.selector().namePatterns().get().get(0));
        assertEquals("jsonPath1", interpolatedView.data().jsonPath());
        assertEquals("validationKind1", interpolatedView.validation().get().asEntityKind());
    }

    @Test
    public void invalidInputIsRejected() {
        var view = new MicaViewV1.Builder()
                .name("test")
                .selector(byEntityKind("${parameters.foo}"))
                .data(jsonPath("$"))
                .parameters(parseObject("""
                        properties:
                          foo:
                            type: string
                        required: ["foo"]
                        """))
                .build();

        // the input is missing the required "foo" parameter
        var input = objectMapper.convertValue(Map.of("bar", "text"), JsonNode.class);
        var error = assertThrows(ValidationErrorsException.class, () -> interpolator.interpolate(view, input));
        assertNotNull(error.getValidationErrors().get(0)); // TODO check the error
    }

    @Test
    public void dropPropertiesAreInterpolated() {
        var view = new MicaViewV1.Builder()
                .name("test")
                .selector(byEntityKind("test"))
                .data(jsonPath("jsonPath")
                        .withDropProperties(List.of("${parameters.drop}", "foobar")))
                .parameters(parseObject("""
                        properties:
                          drop:
                            type: string
                        """))
                .build();

        var input = objectMapper.convertValue(Map.of("drop", "x"), JsonNode.class);
        var interpolatedView = interpolator.interpolate(view, input);
        assertEquals("x", interpolatedView.data().dropProperties().get().get(0));
        assertEquals("foobar", interpolatedView.data().dropProperties().get().get(1));
    }

    private static ObjectNode parseObject(@Language("yaml") String s) {
        try {
            return objectMapper.copyWith(new YAMLFactory()).readValue(s, ObjectNode.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
