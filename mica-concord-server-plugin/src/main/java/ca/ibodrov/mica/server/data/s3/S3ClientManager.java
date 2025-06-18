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
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import javax.inject.Inject;
import java.net.URI;
import java.time.Duration;

import static java.util.Objects.requireNonNull;

public class S3ClientManager {

    private static final Duration SO_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10);

    private final S3CredentialsProvider s3CredentialsProvider;

    @Inject
    public S3ClientManager(S3CredentialsProvider s3CredentialsProvider) {
        this.s3CredentialsProvider = requireNonNull(s3CredentialsProvider);
    }

    public S3Client createClient(QueryParams params) {
        var builder = S3Client.builder()
                .httpClientBuilder(ApacheHttpClient.builder()
                        .socketTimeout(SO_TIMEOUT)
                        .connectionTimeout(CONNECTION_TIMEOUT)
                        .connectionAcquisitionTimeout(CONNECTION_TIMEOUT));

        params.getFirst("secretRef")
                .map(s3CredentialsProvider::get)
                .ifPresentOrElse(
                        builder::credentialsProvider,
                        () -> builder.credentialsProvider(DefaultCredentialsProvider.builder().build()));

        params.getFirst("region")
                .map(Region::of)
                .ifPresent(builder::region);

        params.getFirst("endpoint")
                .map(S3ClientManager::parseEndpoint)
                .ifPresent(builder::endpointOverride);

        return builder.build();
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
}
