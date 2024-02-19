package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.server.YamlMapper;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.walmartlabs.concord.repository.*;
import com.walmartlabs.concord.repository.FetchRequest.Version;
import com.walmartlabs.concord.sdk.Secret;
import com.walmartlabs.concord.server.cfg.GitConfiguration;
import com.walmartlabs.concord.server.cfg.RepositoryConfiguration;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectRepositoryManager;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import com.walmartlabs.concord.server.org.secret.SecretManager.AccessScope;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;

import static ca.ibodrov.mica.server.data.EntityFile.PROPERTIES_KIND;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;

public class ConcordGitEntityFetcher implements EntityFetcher {

    private static final Duration GIT_OPERATION_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration GIT_FETCH_TIMEOUT = Duration.ofSeconds(15);

    private static final String DEFAULT_REF = "main";

    private final OrganizationManager orgManager;
    private final ProjectRepositoryManager projectRepositoryManager;
    private final SecretManager secretManager;
    private final YamlMapper yamlMapper;
    private final RepositoryProviders repositoryProviders;
    private final RepositoryCache repositoryCache;

    @Inject
    public ConcordGitEntityFetcher(OrganizationManager orgManager,
                                   ProjectRepositoryManager projectRepositoryManager,
                                   SecretManager secretManager,
                                   GitConfiguration gitCfg,
                                   RepositoryConfiguration repoCfg,
                                   ObjectMapper objectMapper) {

        this.orgManager = requireNonNull(orgManager);
        this.projectRepositoryManager = requireNonNull(projectRepositoryManager);
        this.secretManager = requireNonNull(secretManager);
        this.yamlMapper = new YamlMapper(objectMapper);

        // use shorter timeouts than Concord's default provider
        var gitCliCfg = GitClientConfiguration.builder()
                .oauthToken(gitCfg.getOauthToken())
                .defaultOperationTimeout(GIT_OPERATION_TIMEOUT)
                .fetchTimeout(GIT_FETCH_TIMEOUT)
                .sshTimeout(GIT_OPERATION_TIMEOUT)
                .sshTimeoutRetryCount(gitCfg.getSshTimeoutRetryCount())
                .build();
        this.repositoryProviders = new RepositoryProviders(List.of(new GitCliRepositoryProvider(gitCliCfg)));

        // use separate repository cache
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
            throw new RuntimeException("Error initializing the repository cache");
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

    @Override
    public List<EntityLike> getAllByKind(URI uri, String kind, int limit) {
        if (!"concord+git".equals(uri.getScheme())) {
            return List.of();
        }

        if (uri.getPath() == null) {
            throw new StoreException("Invalid URI: " + uri);
        }

        var pathElements = uri.getPath().split("/");
        if (pathElements.length != 3) {
            throw new StoreException("Invalid URI: " + uri);
        }

        var orgName = uri.getHost();
        var projectName = pathElements[1];
        var repoName = pathElements[2];
        var queryParams = parseQueryParameters(uri.getQuery());
        var ref = Optional.ofNullable(queryParams.get("ref"))
                .flatMap(r -> Optional.ofNullable(r.get(0)))
                .orElse(DEFAULT_REF);
        var path = Optional.ofNullable(queryParams.get("path"))
                .flatMap(p -> Optional.ofNullable(p.get(0)))
                .orElse("/");
        var useFileNames = Optional.ofNullable(queryParams.get("useFileNames"))
                .flatMap(p -> Optional.ofNullable(p.get(0)))
                .map(Boolean::parseBoolean)
                .orElse(false);
        var namePrefix = Optional.ofNullable(queryParams.get("namePrefix"))
                .flatMap(p -> Optional.ofNullable(p.get(0)))
                .orElse("");
        var allowedFormats = parseAllowedFormats(queryParams);

        return getAllByKind(orgName, projectName, repoName, ref, kind, path, useFileNames, namePrefix, allowedFormats,
                limit);
    }

    private List<EntityLike> getAllByKind(String orgName,
                                          String projectName,
                                          String repoName,
                                          String ref,
                                          String kind,
                                          String pathInRepo,
                                          boolean useFileNames,
                                          String namePrefix,
                                          Set<FileFormat> allowedFormats,
                                          int limit) {

        assert namePrefix != null;
        assert !allowedFormats.isEmpty();

        try {
            var org = orgManager.assertAccess(orgName, false);
            var repoEntry = projectRepositoryManager.get(org.getId(), projectName, repoName);
            var secret = Optional.ofNullable(repoEntry.getSecretName())
                    .map(secretName -> getSecret(org.getId(), secretName));
            return repositoryCache.withLock(repoEntry.getUrl(), () -> {
                var repository = fetch(repoEntry.getUrl(), ref, pathInRepo, secret.orElse(null));
                return walkAndParse(yamlMapper, repository.path(), kind, useFileNames, namePrefix, allowedFormats,
                        limit);
            });
        } catch (StoreException e) {
            throw e;
        } catch (RuntimeException e) {
            // TODO better way
            throw new StoreException(e.getMessage());
        }
    }

    private Secret getSecret(UUID orgId, String secretName) {
        return Optional.ofNullable(secretManager.getSecret(AccessScope.internal(), orgId, secretName, null, null))
                .orElseThrow(() -> new StoreException("Secret not found: " + secretName))
                .getSecret();
    }

    private Repository fetch(String url, String ref, String pathInRepo, Secret secret) {
        Path dest = repositoryCache.getPath(url);
        try {
            return repositoryProviders.fetch(FetchRequest.builder()
                    .url(url)
                    .shallow(true)
                    .checkAlreadyFetched(true)
                    .version(Version.from(ref))
                    .secret(secret)
                    .destination(dest)
                    .build(), pathInRepo);
        } catch (RepositoryException e) {
            throw new StoreException("Error while fetching entities. " + e.getMessage(), e);
        }
    }

    private static Set<FileFormat> parseAllowedFormats(Map<String, List<String>> queryParams) {
        return Optional.ofNullable(queryParams.get("allowedFormats"))
                .flatMap(p -> Optional.ofNullable(p.get(0)))
                .map(s -> {
                    var formats = s.split(",");
                    if (formats.length < 1) {
                        throw new StoreException("Invalid 'allowedFormats': " + s);
                    }
                    var result = ImmutableSet.<FileFormat>builder();
                    for (var f : formats) {
                        try {
                            result.add(FileFormat.valueOf(f.toUpperCase()));
                        } catch (IllegalArgumentException e) {
                            throw new StoreException("Invalid 'allowedFormats' value: " + f);
                        }
                    }
                    return (Set<FileFormat>) result.build();
                })
                .orElse(Set.of(FileFormat.YAML));
    }

    @VisibleForTesting
    static List<EntityLike> walkAndParse(YamlMapper yamlMapper,
                                         Path rootPath,
                                         String kind,
                                         boolean useFileNames,
                                         String namePrefix,
                                         Set<FileFormat> allowedFormats,
                                         int limit) {

        try {
            // noinspection resource
            var data = Files.walk(rootPath)
                    .flatMap(path -> EntityFile.from(path).stream())
                    .filter(file -> allowedFormats.contains(file.format()))
                    .filter(file -> matchesKind(rootPath, file, kind))
                    .map(file -> {
                        var e = file.parseAsEntity(yamlMapper, rootPath);
                        var name = e.name();
                        if (useFileNames) {
                            name = file.path().getFileName().toString();
                            // strip the extension
                            // TODO should this be an option?
                            var idx = name.lastIndexOf('.');
                            if (idx > 0) {
                                name = name.substring(0, idx);
                            }
                        }
                        return e.withName(namePrefix + name);
                    });

            if (limit > 0) {
                data = data.limit(limit);
            }

            return data.toList();
        } catch (IOException e) {
            throw new StoreException("Error while reading the repository: " + e.getMessage(), e);
        }
    }

    private static boolean matchesKind(Path rootPath, EntityFile entityFile, String kindPattern) {
        // should be a cheap way to check if the file is of the given kind
        switch (entityFile.format()) {
            case YAML -> {
                try (var reader = Files.newBufferedReader(entityFile.path(), UTF_8)) {
                    return reader.lines().anyMatch(l -> l.matches("kind:\\s+" + kindPattern));
                } catch (IOException e) {
                    throw new StoreException("Error while reading %s: %s".formatted(entityFile.path(), e.getMessage()),
                            e);
                }
            }
            case PROPERTIES -> {
                return PROPERTIES_KIND.matches(kindPattern);
            }
            default ->
                throw new StoreException("Unsupported file format " + entityFile.format() + ": "
                        + rootPath.relativize(entityFile.path()));
        }
    }

    private static Map<String, List<String>> parseQueryParameters(String s) {
        if (s == null || s.isBlank()) {
            return Map.of();
        }
        return Arrays.stream(s.split("&"))
                .map(ConcordGitEntityFetcher::splitQueryParameter)
                .collect(groupingBy(Map.Entry::getKey, HashMap::new, mapping(Map.Entry::getValue, toList())));
    }

    private static SimpleEntry<String, String> splitQueryParameter(String it) {
        var idx = it.indexOf("=");
        var key = idx > 0 ? it.substring(0, idx) : it;
        var value = idx > 0 && it.length() > idx + 1 ? it.substring(idx + 1) : null;
        return new SimpleEntry<>(
                URLDecoder.decode(key, UTF_8),
                value != null ? URLDecoder.decode(value, UTF_8) : null);
    }
}
