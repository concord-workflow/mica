package ca.ibodrov.mica.server.data.s3;

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

import ca.ibodrov.mica.server.data.QueryParams;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import javax.inject.Inject;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class S3ClientManager {

    private static final Logger log = LoggerFactory.getLogger(S3ClientManager.class);
    private static final Duration INACTIVITY_PERIOD = Duration.ofMinutes(30);
    private static final int MAX_CLIENTS = 10;

    private final S3CredentialsProvider s3CredentialsProvider;
    private final LoadingCache<ClientKey, S3Client> cache;

    @Inject
    public S3ClientManager(S3CredentialsProvider s3CredentialsProvider) {
        this.s3CredentialsProvider = requireNonNull(s3CredentialsProvider);
        this.cache = CacheBuilder.newBuilder()
                .expireAfterAccess(INACTIVITY_PERIOD)
                .maximumSize(MAX_CLIENTS)
                .softValues()
                .removalListener((RemovalListener<ClientKey, S3Client>) notification -> {
                    var client = notification.getValue();
                    if (client != null) {
                        client.close();
                    }
                    log.info("onRemoval -> key={}, cause={}, wasEvicted={}", notification.getKey(),
                            notification.getCause(), notification.wasEvicted());
                })
                .build(new CacheLoader<>() {
                    @Override
                    public S3Client load(ClientKey key) {
                        return createClient(key);
                    }
                });
    }

    public S3Client getClient(QueryParams params) {
        var key = new ClientKey(params.getFirst("secretRef"),
                params.getFirst("region"),
                params.getFirst("endpoint"));
        try {
            return cache.getUnchecked(key);
        } catch (UncheckedExecutionException e) {
            if (e.getCause() instanceof StoreException ex) {
                throw ex;
            }
            throw new StoreException("Failed to get the S3 client: " + e.getMessage());
        }
    }

    private S3Client createClient(ClientKey key) {
        var builder = S3Client.builder();

        key.secretRef()
                .map(s3CredentialsProvider::get)
                .ifPresentOrElse(
                        builder::credentialsProvider,
                        () -> builder.credentialsProvider(DefaultCredentialsProvider.create()));

        key.region()
                .map(Region::of)
                .ifPresent(builder::region);

        key.endpoint()
                .map(S3ClientManager::parseEndpoint)
                .ifPresent(builder::endpointOverride);

        var client = builder.build();
        log.info("createClient -> key={}", key);

        return client;
    }

    private static URI parseEndpoint(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }

        var uri = URI.create(s);
        var host = uri.getHost();
        if (host.equals("127.0.0.1") || host.equals("localhost")) {
            return uri;
        }

        throw new StoreException("Invalid endpoint. Only localhost or 127.0.0.1 are allowed as S3 endpoint overrides.");
    }

    private record ClientKey(Optional<String> secretRef, Optional<String> region, Optional<String> endpoint) {
    }
}
