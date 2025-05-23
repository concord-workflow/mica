package ca.ibodrov.mica.api.validation;

/*-
 * ~~~~~~
 * Mica
 * ------
 * Copyright (C) 2023 - 2025 Mica Authors
 * ------
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ======
 */

import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.constraints.Pattern;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Pattern(regexp = ValidName.NAME_PATTERN, message = ValidName.MESSAGE)
@Retention(RUNTIME)
@Target({ FIELD, TYPE_USE })
@Constraint(validatedBy = ValidNameValidator.class)
public @interface ValidName {

    String NAME_PATTERN = "/[a-zA-Z0-9.\\-_/\\\\]{3,1023}";

    String MESSAGE = "names should start with a forward slash '/' and can include lowercase and uppercase " +
            "letters (a-z, A-Z), digits, dots, dashes, and forward slashes. Should not contain two " +
            "consecutive forward slashes. Length must be between 4 and 1024 characters (including " +
            "the first slash).";

    String message() default MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
