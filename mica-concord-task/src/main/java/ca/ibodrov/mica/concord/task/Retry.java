package ca.ibodrov.mica.concord.task;

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
        var retries = 0;
        var retryDelay = INITIAL_RETRY_DELAY;

        while (true) {
            try {
                return call.call();
            } catch (Exception e) {
                if (retries >= MAX_RETRIES) {
                    throw e;
                }

                var status = -1;
                if (e instanceof ApiException) {
                    status = ((ApiException) e).getStatus();
                }
                log.info("Retrying after an API error (status={}, attempt={}, next in {}s): {}", status,
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
