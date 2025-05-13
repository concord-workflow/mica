package ca.ibodrov.mica.server.data.s3;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.data.EntityFetcher;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import com.walmartlabs.concord.server.org.secret.SecretType;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.walmartlabs.concord.server.org.secret.SecretManager.AccessScope.apiRequest;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class S3EntityFetcher implements EntityFetcher {

    private static final String URI_SCHEME = "s3";
    private static final String DEFAULT_ENTITY_KIND = "/s3/object/v1";
    private static final long DEFAULT_MAX_BYTES = 5 * 1024 * 1024;
    private static final int DEFAULT_BATCH_SIZE = 10; // TODO make configurable

    private static final TypeReference<Map<String, JsonNode>> MAP_OF_JSON_NODES = new TypeReference<>() {
    };

    private final OrganizationManager orgManager;
    private final SecretManager secretManager;
    private final ObjectMapper objectMapper;

    @Inject
    public S3EntityFetcher(OrganizationManager orgManager,
                           SecretManager secretManager,
                           ObjectMapper objectMapper) {

        this.orgManager = requireNonNull(orgManager);
        this.secretManager = requireNonNull(secretManager);
        this.objectMapper = requireNonNull(objectMapper);
    }

    @VisibleForTesting
    S3EntityFetcher(ObjectMapper objectMapper) {
        this.orgManager = null;
        this.secretManager = null;
        this.objectMapper = objectMapper;
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

        var client = createClient(params);

        var bucketName = uri.getHost();
        var objectName = normalizeObjectName(uri.getPath());
        var kind = Optional.ofNullable(params.get("defaultKind")).orElse(DEFAULT_ENTITY_KIND);
        var batchSize = Optional.ofNullable(params.get("batchSize")).map(Integer::parseInt).orElse(DEFAULT_BATCH_SIZE);

        if (objectName == null || objectName.isBlank()) {
            return () -> fetchAllEntities(client, bucketName, kind, batchSize).onClose(client::close);
        } else {
            return () -> {
                var entity = fetchEntity(client, bucketName, objectName, kind);
                return Stream.of(entity).onClose(client::close);
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

    private S3Client createClient(Map<String, String> params) {
        var builder = S3Client.builder();

        // credentials
        Optional.ofNullable(params.get("secretRef"))
                .map(this::fetchCredentials)
                .ifPresentOrElse(
                        builder::credentialsProvider,
                        () -> builder.credentialsProvider(DefaultCredentialsProvider.create()));

        // region
        Optional.ofNullable(params.get("region"))
                .map(Region::of)
                .ifPresent(builder::region);

        // endpoint
        Optional.ofNullable(params.get("endpoint"))
                .map(S3EntityFetcher::parseEndpoint)
                .ifPresent(builder::endpointOverride);

        return builder.build();
    }

    private StaticCredentialsProvider fetchCredentials(String secretRef) {
        secretRef = secretRef.trim();

        if (secretRef.isBlank()) {
            throw new StoreException("Invalid secretRef. Expected orgName/secretName format, got a blank value");
        }

        var idx = secretRef.indexOf("/");
        if (idx <= 0 || idx + 1 >= secretRef.length()) {
            throw new StoreException("Invalid secretRef. Expected orgName/secretName format, got: " + secretRef);
        }

        var orgName = secretRef.substring(0, idx);
        if (!orgName.matches(ConcordKey.PATTERN)) {
            throw new StoreException("Invalid secretRef. Expected an organization name, got: " + orgName);
        }

        var secretName = secretRef.substring(idx + 1);
        if (!secretName.matches(ConcordKey.PATTERN)) {
            throw new StoreException("Invalid secretRef. Expected a secret name, got: " + orgName);
        }

        try {
            var org = requireNonNull(orgManager).assertAccess(orgName, false);
            var secretContainer = requireNonNull(secretManager).getSecret(apiRequest(), org.getId(), secretName, null,
                    SecretType.USERNAME_PASSWORD);
            var secret = secretContainer.getSecret();
            if (secret instanceof UsernamePassword credentials) {
                return StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(credentials.getUsername(), new String(credentials.getPassword())));
            } else {
                throw new StoreException(
                        "Invalid secretRef. Expected an username/password secret, got: " + secret.getClass());
            }
        } catch (WebApplicationException e) {
            throw new StoreException("Can't fetch the secretRef. " + e.getMessage());
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
