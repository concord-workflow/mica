package ca.ibodrov.mica.server.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.networknt.schema.ValidationMessage;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorXO;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;

import java.util.Set;
import java.util.UUID;

import static java.util.Comparator.comparing;

public record ValidatedInput(Set<ValidationMessage> messages) {

    @JsonIgnore
    public boolean isValid() {
        return messages.isEmpty();
    }

    @JsonIgnore
    public ValidationErrorsException toException() {
        if (isValid()) {
            throw new IllegalStateException("Input is valid");
        }

        return new ValidationErrorsException()
                .withErrors(messages.stream()
                        .sorted(comparing(ValidationMessage::getPath).thenComparing(ValidationMessage::getMessage))
                        .map(m -> new ValidationErrorXO(UUID.randomUUID().toString(), m.getMessage()))
                        .toList());
    }
}
