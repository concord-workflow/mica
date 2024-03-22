package ca.ibodrov.mica.server.data.git;

import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.repository.*;
import com.walmartlabs.concord.sdk.Secret;
import com.walmartlabs.concord.server.cfg.GitConfiguration;
import com.walmartlabs.concord.server.cfg.RepositoryConfiguration;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public class GitUrlFetcher {

    private static final Duration GIT_OPERATION_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration GIT_FETCH_TIMEOUT = Duration.ofSeconds(15);

    private final RepositoryCache repositoryCache;
    private final RepositoryProviders repositoryProviders;

    public GitUrlFetcher(GitConfiguration gitCfg,
                         RepositoryConfiguration repoCfg,
                         ObjectMapper objectMapper) {

        // use shorter timeouts than Concord's default provider
        var gitCliCfg = GitClientConfiguration.builder()
                .oauthToken(gitCfg.getOauthToken())
                .defaultOperationTimeout(GIT_OPERATION_TIMEOUT)
                .fetchTimeout(GIT_FETCH_TIMEOUT)
                .sshTimeout(GIT_OPERATION_TIMEOUT)
                .sshTimeoutRetryCount(gitCfg.getSshTimeoutRetryCount())
                .build();
        this.repositoryProviders = new RepositoryProviders(List.of(new GitCliRepositoryProvider(gitCliCfg)));

        // use separate repository cache from Concord's
        try {
            var cacheDir = mkdirs(repoCfg.getCacheDir(), "_micaCache");
            var cacheInfoDir = mkdirs(repoCfg.getCacheInfoDir(), "_micaInfoCache");
            var lockTimeout = GIT_OPERATION_TIMEOUT.multipliedBy(2);
            this.repositoryCache = new RepositoryCache(cacheDir,
                    cacheInfoDir,
                    lockTimeout,
                    repoCfg.getMaxAge(),
                    repoCfg.getLockCount(),
                    objectMapper);
        } catch (IOException e) {
            throw new RuntimeException("Repository cache initialization error: " + e.getMessage(), e);
        }
    }

    public <T> Stream<T> fetch(String url,
                               String ref,
                               String pathInRepo,
                               @Nullable Secret secret,
                               Function<Repository, Stream<T>> fetcher) {
        try {
            return repositoryCache.withLock(url, () -> {
                var repository = fetch(url, ref, pathInRepo, secret);
                return fetcher.apply(repository);
            });
        } catch (StoreException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new StoreException(e.getMessage());
        }
    }

    private Repository fetch(String url, String ref, String pathInRepo, Secret secret) {
        var dest = repositoryCache.getPath(url);
        try {
            return repositoryProviders.fetch(FetchRequest.builder()
                    .url(url)
                    .shallow(true)
                    .checkAlreadyFetched(true)
                    .version(FetchRequest.Version.from(ref))
                    .secret(secret)
                    .destination(dest)
                    .build(), pathInRepo);
        } catch (RepositoryException e) {
            throw new StoreException("Error while fetching entities. " + e.getMessage(), e);
        }
    }

    private static Path mkdirs(Path dir, String name) {
        var path = dir.resolve(name);
        if (Files.exists(path)) {
            if (!Files.isDirectory(path)) {
                throw new RuntimeException("Expected a directory: " + path);
            }
            return path;
        }
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new RuntimeException("Error while creating directories: " + path);
        }
        return path;
    }
}
