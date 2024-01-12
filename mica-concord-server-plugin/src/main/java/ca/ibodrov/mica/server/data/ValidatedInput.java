package ca.ibodrov.mica.server.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.networknt.schema.ValidationMessage;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorXO;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;

import java.util.Set;

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

        // TODO better error messages, sort by path
        return new ValidationErrorsException()
                .withErrors(messages.stream().map(m -> new ValidationErrorXO(m.getMessage()))
                        .toList());
    }
}
