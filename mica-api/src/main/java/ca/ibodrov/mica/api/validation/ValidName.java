package ca.ibodrov.mica.api.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@NotEmpty
@Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9.\\-_/\\\\]{2,255}")
@Retention(RUNTIME)
@Constraint(validatedBy = {})
public @interface ValidName {

    String message() default "Names should start with a letter or digit, and can include lowercase and uppercase " +
            "letters (a-z, A-Z), digits, dots, dashes, and slashes. Length must be between 3 and 256 characters..";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
