package ca.ibodrov.mica.server.api;

import com.walmartlabs.concord.common.DateTimeUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

public final class ApiUtils {

    public static String nonBlank(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s;
    }

    public static Optional<Instant> parseIsoAsInstant(String s) {
        return Optional.ofNullable(nonBlank(s))
                .map(DateTimeUtils::fromIsoString)
                .map(OffsetDateTime::toInstant);
    }

    private ApiUtils() {
    }
}
