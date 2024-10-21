package ca.ibodrov.mica.concord.task;

import org.slf4j.Logger;

import java.time.Duration;

public final class Retry {

    private static final int MAX_RETRIES = 5;
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(1);
    private static final double BACKOFF_MULTIPLIER = 2.0;

    public interface Retryable<T> {

        T call() throws ApiException;
    }

    public static <T> T withRetry(Logger log, Retryable<T> call) throws ApiException {
        int retries = 0;
        Duration retryDelay = INITIAL_RETRY_DELAY;

        while (true) {
            try {
                return call.call();
            } catch (ApiException e) {
                if (retries >= MAX_RETRIES) {
                    throw e;
                }

                log.info("Retrying after an API error (status={}, attempt={}, next in {}s): {}", e.getStatus(),
                        retries + 1, retryDelay.toSeconds(), e.getMessage());

                try {
                    Thread.sleep(retryDelay.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }

                retries++;
                retryDelay = Duration.ofMillis((long) (retryDelay.toMillis() * BACKOFF_MULTIPLIER));
            }
        }
    }
}
