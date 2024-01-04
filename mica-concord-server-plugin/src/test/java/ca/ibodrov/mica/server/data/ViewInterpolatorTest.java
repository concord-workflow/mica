package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.kinds.MicaViewV1;
import ca.ibodrov.mica.server.exceptions.EntityValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ca.ibodrov.mica.api.kinds.MicaViewV1.Data.jsonPath;
import static ca.ibodrov.mica.api.kinds.MicaViewV1.Selector.byEntityKind;
import static ca.ibodrov.mica.api.kinds.MicaViewV1.Validation.asEntityKind;
import static ca.ibodrov.mica.schema.ObjectSchemaNode.object;
import static ca.ibodrov.mica.schema.ObjectSchemaNode.string;
import static ca.ibodrov.mica.schema.ValidationError.Kind.MISSING_PROPERTY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ViewInterpolatorTest {

    private static final ViewInterpolator interpolator = new ViewInterpolator(ref -> Optional.empty());

    @Test
    public void unresolvedPlaceholdersMustBeEscaped() {
        var view = new MicaViewV1.Builder()
                .name("test")
                .selector(byEntityKind("${parameters.kind}")
                        .withIncludes(List.of("${parameters.include}"))
                        .withNamePatterns(List.of("${parameters.name}")))
                .data(jsonPath("${parameters.jsonPath}"))
                .validation(asEntityKind("${parameters.validationKind}"))
                .parameters(object(Map.of(
                        "kind", string(),
                        "include", string(),
                        "name", string(),
                        "jsonPath", string(),
                        "validationKind", string()), Set.of()))
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
                .parameters(object(Map.of(
                        "kind", string(),
                        "include", string(),
                        "name", string(),
                        "jsonPath", string(),
                        "validationKind", string()), Set.of()))
                .build();

        var interpolatedView = interpolator.interpolate(view,
                new ObjectMapper().convertValue(Map.of(
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
                .parameters(object(Map.of("foo", string()), Set.of("foo")))
                .build();

        // the input is missing the required "foo" parameter
        var input = new ObjectMapper().convertValue(Map.of("bar", "text"), JsonNode.class);
        var error = assertThrows(EntityValidationException.class, () -> interpolator.interpolate(view, input));
        assertEquals(MISSING_PROPERTY, error.getErrors().get(".foo").kind());
    }
}
