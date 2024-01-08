package ca.ibodrov.mica.api.model;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;

public record BatchOperationRequest(@NotNull BatchOperation operation, Optional<List<String>> namePatterns) {

    public static BatchOperationRequest deleteByNamePatterns(List<String> namePatterns) {
        return new BatchOperationRequest(BatchOperation.DELETE, Optional.of(namePatterns));
    }
}
