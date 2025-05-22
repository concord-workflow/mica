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

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.data.EntityFetcher;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class S3EntityFetcher implements EntityFetcher {

    private static final String URI_SCHEME = "s3";
    private static final String DEFAULT_ENTITY_KIND = "/s3/object/v1";
    private static final long DEFAULT_MAX_BYTES = 5 * 1024 * 1024;
    private static final int DEFAULT_BATCH_SIZE = 10; // TODO make configurable

    private static final TypeReference<Map<String, JsonNode>> MAP_OF_JSON_NODES = new TypeReference<>() {
    };

    private final S3ClientManager clientManager;
    private final ObjectMapper objectMapper;

    @Inject
    public S3EntityFetcher(S3ClientManager clientManager, ObjectMapper objectMapper) {
        this.clientManager = requireNonNull(clientManager);
        this.objectMapper = requireNonNull(objectMapper);
    }

    @Override
    public boolean isSupported(FetchRequest request) {
        return request.uri()
                .map(uri -> URI_SCHEME.equals(uri.getScheme()))
                .orElse(false);
    }

    @Override
    public Cursor fetch(FetchRequest request) {
        var uri = request.uri().orElseThrow(() -> new StoreException("s3:// URI is required"));
        var params = parseQueryParams(uri);

        var client = clientManager.getClient(params);

        var bucketName = uri.getHost();
        var objectName = normalizeObjectName(uri.getPath());
        var kind = Optional.ofNullable(params.get("defaultKind")).orElse(DEFAULT_ENTITY_KIND);
        var batchSize = Optional.ofNullable(params.get("batchSize")).map(Integer::parseInt).orElse(DEFAULT_BATCH_SIZE);

        if (objectName == null || objectName.isBlank()) {
            return () -> fetchAllEntities(client, bucketName, kind, batchSize);
        } else {
            return () -> {
                var entity = fetchEntity(client, bucketName, objectName, kind);
                return Stream.of(entity);
            };
        }
    }

    private Stream<EntityLike> fetchAllEntities(S3Client client, String bucketName, String kind, int batchSize) {
        var iterator = new S3ObjectIterator(client, bucketName, batchSize);
        var objects = StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
        return objects.map(object -> fetchEntity(client, bucketName, object.key(), kind));
    }

    private EntityLike fetchEntity(S3Client client, String bucketName, String objectName, String kind) {
        var getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectName)
                .range("bytes=0-" + DEFAULT_MAX_BYTES)
                .build();

        try {
            var response = client.getObject(getObjectRequest, ResponseTransformer.toInputStream());
            try {
                var data = objectMapper.readValue(response, MAP_OF_JSON_NODES);
                return PartialEntity.create(bucketName + "/" + objectName, kind, data);
            } catch (IOException e) {
                throw new StoreException("Can't parse S3 object %s/%s as JSON: %s".formatted(bucketName,
                        objectName, e.getMessage()));
            }
        } catch (NoSuchKeyException e) {
            throw new StoreException("Object not found: " + bucketName + "/" + objectName);
        } catch (Exception e) {
            throw new StoreException(e.getMessage());
        }
    }

    private static Map<String, String> parseQueryParams(URI uri) {
        var query = uri.getQuery();
        if (query == null || query.isBlank()) {
            return Map.of();
        }

        var result = new HashMap<String, String>();
        var pairs = query.split("&");
        for (var pair : pairs) {
            var idx = pair.indexOf("=");
            var key = URLDecoder.decode(pair.substring(0, idx), UTF_8);
            var value = idx + 1 < pair.length() ? URLDecoder.decode(pair.substring(idx + 1), UTF_8) : "";
            result.put(key, value);
        }
        return result;
    }

    private static String normalizeObjectName(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }

        if (s.startsWith("/")) {
            return s.substring(1);
        }

        return s;
    }

    @VisibleForTesting
    static class S3ObjectIterator implements Iterator<S3Object> {

        private final S3Client client;
        private final String bucketName;
        private final int batchSize;

        private String nextContinuationToken;
        private Iterator<S3Object> currentBatch;
        private boolean lastBatch;

        S3ObjectIterator(S3Client client, String bucketName, int batchSize) {
            this.client = client;
            this.bucketName = bucketName;
            this.batchSize = batchSize;
        }

        @Override
        public boolean hasNext() {
            if (currentBatch == null || !currentBatch.hasNext()) {
                if (lastBatch) {
                    return false;
                }
                fetchNext();
            }
            return currentBatch.hasNext();
        }

        @Override
        public S3Object next() {
            if (!hasNext()) {
                throw new StoreException("No more objects available");
            }
            return currentBatch.next();
        }

        private void fetchNext() {
            var requestBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .maxKeys(batchSize);

            if (nextContinuationToken != null) {
                requestBuilder.continuationToken(nextContinuationToken);
            }

            var response = client.listObjectsV2(requestBuilder.build());

            currentBatch = response.contents().iterator();
            nextContinuationToken = response.nextContinuationToken();

            if (nextContinuationToken == null) {
                lastBatch = true;
            }
        }
    }
}
