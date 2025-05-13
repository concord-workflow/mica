package ca.ibodrov.mica.server.data.s3;

import ca.ibodrov.mica.server.data.EntityFetcher.FetchRequest;
import ca.ibodrov.mica.server.data.s3.S3EntityFetcher.S3ObjectIterator;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Disabled
public class S3EntityFetcherTest {

    @Test
    public void testFetch() {
        var bucketName = System.getenv("TEST_BUCKET");
        var fetcher = new S3EntityFetcher(new ObjectMapperProvider().get());
        var uri = URI.create("s3://" + bucketName + "?withKind=/mica/v1/record");
        var cursor = fetcher.fetch(FetchRequest.ofUri(uri));
        var result = cursor.stream().toList();
        System.out.println(result);
    }

    @Test
    public void testIterator() {
        var bucketName = System.getenv("TEST_BUCKET");

        var client = S3Client.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        var iterator = new S3ObjectIterator(client, bucketName, 1);
        System.out.println(iterator.next());
        System.out.println(iterator.next());
    }
}
