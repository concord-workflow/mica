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
import ca.ibodrov.mica.server.data.QueryParams;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.annotations.VisibleForTesting;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import javax.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Objects.requireNonNull;

public class S3EntityFetcher implements EntityFetcher {

    private static final String URI_SCHEME = "s3";
    private static final String DEFAULT_ENTITY_KIND = "/s3/object/v1";
    private static final long DEFAULT_MAX_BYTES = 5 * 1024 * 1024;
    private static final int DEFAULT_BATCH_SIZE = 10; // TODO make configurable
    private static final int MAX_RESULTS = 50; // TODO make configurable

    private static final TypeReference<Map<String, JsonNode>> MAP_OF_JSON_NODES = new TypeReference<>() {
    };

    private final S3ClientManager clientManager;
    private final ObjectMapper jsonMapper;
    private final YAMLMapper yamlMapper;

    @Inject
    public S3EntityFetcher(S3ClientManager clientManager, ObjectMapper objectMapper) {
        this.clientManager = requireNonNull(clientManager);
        this.jsonMapper = requireNonNull(objectMapper);
        this.yamlMapper = YAMLMapper.builder().build();
    }

    @Override
    public boolean isSupported(FetchRequest request) {
        return request.uri()
                .map(uri -> URI_SCHEME.equals(uri.getScheme()))
                .orElse(false);
    }

    @Override
    public Cursor fetch(FetchRequest request) {
        var uri = request.uri().orElseThrow(() -> new StoreException(URI_SCHEME + ":// URI is required"));
        var params = new QueryParams(uri.getQuery());
        var bucketName = uri.getHost();
        var objectName = normalizeObjectName(uri.getPath());
        var namePattern = params.getFirst("namePattern").map(S3EntityFetcher::compileNamePattern).orElse(null);
        var kind = params.getFirst("defaultKind").orElse(DEFAULT_ENTITY_KIND);
        var batchSize = params.getFirst("batchSize").map(Integer::parseInt).orElse(DEFAULT_BATCH_SIZE);

        if (objectName != null && namePattern != null) {
            throw new StoreException("The 'namePattern' parameter cannot be used when fetching a specific object.");
        }

        if (objectName == null || objectName.isBlank()) {
            return () -> {
                var client = clientManager.createClient(params);
                return fetchAllEntities(client, bucketName, kind, namePattern, batchSize)
                        .limit(MAX_RESULTS)
                        .onClose(client::close);
            };
        } else {
            return () -> {
                var client = clientManager.createClient(params);
                var entity = fetchEntity(client, bucketName, objectName, kind);
                return Stream.of(entity).onClose(client::close);
            };
        }
    }

    private Stream<EntityLike> fetchAllEntities(S3Client client,
                                                String bucketName,
                                                String kind,
                                                Pattern namePattern,
                                                int batchSize) {
        var iterator = new S3ObjectIterator(client, bucketName, batchSize);
        var objects = StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
                .filter(o -> namePattern == null || namePattern.matcher(o.key()).matches());
        return objects.map(object -> fetchEntity(client, bucketName, object.key(), kind));
    }

    private EntityLike fetchEntity(S3Client client, String bucketName, String objectName, String defaultKind) {
        var getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectName)
                .range("bytes=0-" + DEFAULT_MAX_BYTES)
                .build();

        try {
            var response = client.getObject(getObjectRequest, ResponseTransformer.toInputStream());
            try {
                return parse(bucketName, objectName, defaultKind, response);
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

    private EntityLike parse(String bucketName,
                             String objectName,
                             String defaultKind,
                             ResponseInputStream<GetObjectResponse> response)
            throws IOException {

        Map<String, JsonNode> data;
        if (objectName.endsWith(".json")) {
            data = jsonMapper.readValue(response, MAP_OF_JSON_NODES);
        } else if (objectName.endsWith(".yml") || objectName.endsWith(".yaml")) {
            data = yamlMapper.readValue(response, MAP_OF_JSON_NODES);
        } else {
            throw new StoreException("Can't parse %s/%s - only .json, .yaml or .yml files are supported."
                    .formatted(bucketName, objectName));
        }

        data = new HashMap<>(data);

        var name = Optional.ofNullable(data.remove("name")).map(JsonNode::asText)
                .orElseGet(() -> "/" + bucketName + "/" + objectName);
        var kind = Optional.ofNullable(data.remove("kind")).map(JsonNode::asText).orElse(defaultKind);
        var createdAt = Optional.ofNullable(data.remove("createdAt")).map(JsonNode::asText).map(Instant::parse);
        var updatedAt = Optional.ofNullable(data.remove("updatedAt")).map(JsonNode::asText).map(Instant::parse);

        return new PartialEntity(Optional.empty(), name, kind, createdAt, updatedAt, Optional.empty(), data);
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

    private static Pattern compileNamePattern(String s) {
        try {
            return Pattern.compile(s);
        } catch (PatternSyntaxException e) {
            throw new StoreException("Invalid 'namePattern' parameter: " + e.getMessage());
        }
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
