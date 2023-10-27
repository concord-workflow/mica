package org.acme.mica.server.api;

import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@NotEmpty
@Pattern(regexp = "[a-z0-9][a-z0-9.\\-_/\\\\]{2,127}")
@Retention(RUNTIME)
@Constraint(validatedBy = {})
public @interface ValidClientName {

    String message() default "client names must start with a letter or a digit, contain only " +
            "lowercase letters, digits, dots, dashes and slashes, and be between 3 and 128 characters in length";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
