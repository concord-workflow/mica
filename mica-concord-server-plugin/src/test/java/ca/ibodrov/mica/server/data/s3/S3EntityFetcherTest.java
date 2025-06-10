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
import ca.ibodrov.mica.server.data.EntityFetcher.FetchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;

import static java.util.Comparator.comparing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

public class S3EntityFetcherTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final LocalStackContainer localStack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:s3-latest"))
            .withServices(S3);

    private static S3Client s3Client;
    private static S3EntityFetcher fetcher;
    private String bucketName;

    @BeforeAll
    public static void setUp() {
        localStack.start();

        var localStackCredentials = StaticCredentialsProvider
                .create(AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey()));

        s3Client = S3Client.builder()
                .endpointOverride(localStack.getEndpointOverride(S3))
                .credentialsProvider(localStackCredentials)
                .region(Region.of(localStack.getRegion()))
                .build();

        var credentialsProvider = mock(S3CredentialsProvider.class);
        when(credentialsProvider.get(anyString())).thenReturn(localStackCredentials);

        var clientManager = new S3ClientManager(credentialsProvider);
        fetcher = new S3EntityFetcher(clientManager, objectMapper);
    }

    @AfterAll
    public static void tearDown() {
        localStack.stop();
    }

    @BeforeEach
    public void initBucket() {
        bucketName = "test-" + System.currentTimeMillis();
        s3Client.createBucket(CreateBucketRequest.builder()
                .bucket(bucketName)
                .build());
    }

    @Test
    public void fetchPlainJson() {
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucketName)
                .key("test.json")
                .build(), RequestBody.fromString("""
                        {
                            "foo": "bar"
                        }
                        """));

        var fetchRequest = FetchRequest
                .ofUri(URI.create("s3://%s/test.json?endpoint=%s&region=%s&secretRef=test/test"
                        .formatted(bucketName, localStack.getEndpointOverride(S3), localStack.getRegion())));

        var result = fetcher.fetch(fetchRequest).stream().toList();
        assertEquals(1, result.size());
        assertEquals("bar", result.get(0).data().get("foo").asText());
    }

    @Test
    public void fetchAll() {
        var entityCount = 10;

        for (var i = 0; i < entityCount; i++) {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key("test%s.json".formatted(i))
                    .build(), RequestBody.fromString("""
                            {
                                "num": %s
                            }
                            """.formatted(i)));
        }

        var fetchRequest = FetchRequest
                .ofUri(URI.create("s3://%s?endpoint=%s&region=%s&secretRef=test/test"
                        .formatted(bucketName, localStack.getEndpointOverride(S3), localStack.getRegion())));

        var result = fetcher.fetch(fetchRequest).stream().toList();
        assertEquals(entityCount, result.size());
    }

    @Test
    public void fetchSubset() {
        var entityCount = 10;

        for (var i = 0; i < entityCount; i++) {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key("test%s.json".formatted(i))
                    .build(), RequestBody.fromString("""
                            {
                                "num": %s
                            }
                            """.formatted(i)));
        }

        var fetchRequest = FetchRequest
                .ofUri(URI.create("s3://%s?endpoint=%s&region=%s&secretRef=test/test&namePattern=test[1-3].json"
                        .formatted(bucketName, localStack.getEndpointOverride(S3), localStack.getRegion())));

        var result = fetcher.fetch(fetchRequest).stream().sorted(comparing(EntityLike::name)).toList();
        assertEquals(3, result.size());
        assertTrue(result.get(0).name().contains("test1.json"));
        assertTrue(result.get(1).name().contains("test2.json"));
        assertTrue(result.get(2).name().contains("test3.json"));
    }

    @Test
    public void fetchYaml() {
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucketName)
                .key("test.yaml")
                .build(), RequestBody.fromString("""
                        name: /test
                        kind: /mica/record/v1
                        createdAt: "2025-06-06T00:00:02.510095Z"
                        data:
                          foo: "bar"
                        """));

        var fetchRequest = FetchRequest
                .ofUri(URI.create("s3://%s/test.yaml?endpoint=%s&region=%s&secretRef=test/test"
                        .formatted(bucketName, localStack.getEndpointOverride(S3), localStack.getRegion())));

        var result = fetcher.fetch(fetchRequest).stream().toList();
        assertEquals(1, result.size());
        var entity = result.get(0);
        assertEquals("/test", entity.name());
        assertEquals("/mica/record/v1", entity.kind());
        assertEquals("bar", entity.data().get("data").get("foo").asText());
    }
}
