package ca.ibodrov.mica.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING;
import static java.util.Objects.requireNonNull;

public record LibraryId(UUID id) {

    @JsonCreator(mode = DELEGATING)
    public LibraryId {
        requireNonNull(id);
    }

    @Override
    @JsonValue
    public UUID id() {
        return id;
    }

    public String toExternalForm() {
        return id.toString();
    }

    public static LibraryId fromString(String s) {
        return new LibraryId(UUID.fromString(s));
    }
}
