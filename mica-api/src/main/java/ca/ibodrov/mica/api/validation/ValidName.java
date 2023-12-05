package ca.ibodrov.mica.api.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@NotEmpty
@Pattern(regexp = "/[a-zA-Z0-9.\\-_/\\\\]{3,1023}")
@Retention(RUNTIME)
@Constraint(validatedBy = {})
public @interface ValidName {

    String message() default "Names should start with a forward slash '/' and can include lowercase and uppercase " +
            "letters (a-z, A-Z), digits, dots, dashes, and forward slashes. Length must be between 4 and 1024 " +
            "characters (including the first slash).";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
