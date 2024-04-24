package ca.ibodrov.mica.server.data.git;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.server.YamlMapper;
import ca.ibodrov.mica.server.data.EntityFetcher;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.walmartlabs.concord.repository.Repository;
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
import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static ca.ibodrov.mica.server.data.git.EntityFile.PROPERTIES_KIND;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;

public class ConcordGitEntityFetcher implements EntityFetcher {

    private static final String URI_SCHEME = "concord+git";
    private static final String DEFAULT_REF = "main";
    private static final Set<FileFormat> DEFAULT_ALLOWED_FORMATS = Set.of(FileFormat.YAML);
    private static final String DEFAULT_YAML_FILE_PATTERN = ".*\\.ya?ml";
    private static final String DEFAULT_PROPERTIES_FILE_PATTERN = ".*\\.properties";

    @VisibleForTesting
    static final Map<FileFormat, FileFormatOptions> DEFAULT_FILE_FORMAT_OPTIONS = Map.of(
            FileFormat.YAML, new FileFormatOptions(DEFAULT_YAML_FILE_PATTERN),
            FileFormat.PROPERTIES, new FileFormatOptions(DEFAULT_PROPERTIES_FILE_PATTERN));

    private final OrganizationManager orgManager;
    private final ProjectRepositoryManager projectRepositoryManager;
    private final SecretManager secretManager;
    private final YamlMapper yamlMapper;
    private final GitUrlFetcher gitUrlFetcher;

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
        this.gitUrlFetcher = new GitUrlFetcher(gitCfg, repoCfg, objectMapper);
    }

    @Override
    public boolean isSupported(URI uri) {
        return URI_SCHEME.equals(uri.getScheme());
    }

    @Override
    public Cursor getAllByKind(URI uri, String kind, int limit) {
        var query = Query.parseWithKind(uri, kind);
        return getAllByKind(query);
    }

    private Cursor getAllByKind(Query query) {
        return fetch(query, repository -> {
            try {
                return walkAndParse(
                        yamlMapper,
                        repository.path(),
                        query.kind,
                        query.useFileNames,
                        query.namePrefix,
                        query.allowedFormats,
                        query.formatOptions);
            } catch (IOException e) {
                throw new StoreException("Error while reading entities: " + e.getMessage(), e);
            }
        });
    }

    private Cursor fetch(Query query, Function<Repository, Stream<EntityLike>> fetcher) {
        try {
            var org = orgManager.assertAccess(query.orgName, false);
            var repoEntry = projectRepositoryManager.get(org.getId(), query.projectName, query.repoName);
            var secret = Optional.ofNullable(repoEntry.getSecretName())
                    .map(secretName -> getSecret(org.getId(), secretName));
            var data = gitUrlFetcher.fetch(repoEntry.getUrl(), query.ref, query.pathInRepo, secret.orElse(null),
                    fetcher);
            if (query.limit > 0) {
                data = data.limit(query.limit);
            }
            var finalData = data;
            return () -> finalData;
        } catch (StoreException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new StoreException(e.getMessage());
        }
    }

    private Secret getSecret(UUID orgId, String secretName) {
        return Optional.ofNullable(secretManager.getSecret(AccessScope.internal(), orgId, secretName, null, null))
                .orElseThrow(() -> new StoreException("Secret not found: " + secretName))
                .getSecret();
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
                .orElse(DEFAULT_ALLOWED_FORMATS);
    }

    private static Map<FileFormat, FileFormatOptions> parseFormatOptions(Set<FileFormat> allowedFormats,
                                                                         Map<String, List<String>> queryParams) {
        return allowedFormats.stream()
                .collect(toMap(f -> f, f -> {
                    var pattern = Optional.ofNullable(queryParams.get(f.name().toLowerCase() + ".filePattern"))
                            .flatMap(p -> Optional.ofNullable(p.get(0)))
                            .orElseGet(() -> {
                                switch (f) {
                                    case YAML -> {
                                        return DEFAULT_YAML_FILE_PATTERN;
                                    }
                                    case PROPERTIES -> {
                                        return DEFAULT_PROPERTIES_FILE_PATTERN;
                                    }
                                    default -> throw new StoreException("Unsupported file format: " + f);
                                }
                            });
                    return new FileFormatOptions(pattern);
                }));
    }

    @VisibleForTesting
    static Stream<EntityLike> walkAndParse(YamlMapper yamlMapper,
                                           Path rootPath,
                                           String kind,
                                           boolean useFileNames,
                                           String namePrefix,
                                           Set<FileFormat> allowedFormats,
                                           Map<FileFormat, FileFormatOptions> formatOptions)
            throws IOException {

        // noinspection resource
        return Files.walk(rootPath)
                .flatMap(path -> parsePath(path, allowedFormats, formatOptions).stream())
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
    }

    private static Optional<EntityFile> parsePath(Path path,
                                                  Set<FileFormat> allowedFormats,
                                                  Map<FileFormat, FileFormatOptions> formatOptions) {

        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }

        var fileName = path.getFileName().toString();
        var format = formatOptions.entrySet().stream()
                .filter(kv -> allowedFormats.contains(kv.getKey()))
                .filter(kv -> fileName.matches(kv.getValue().fileNamePattern()))
                .findFirst()
                .map(Map.Entry::getKey);

        return format.map(fileFormat -> new EntityFile(fileFormat, path));

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
            default -> throw new StoreException("Unsupported file format " + entityFile.format() + ": "
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

    private record Query(String orgName,
            String projectName,
            String repoName,
            String ref,
            String kind,
            String pathInRepo,
            boolean useFileNames,
            String namePrefix,
            Set<FileFormat> allowedFormats,
            Map<FileFormat, FileFormatOptions> formatOptions,
            int limit) {

        static Query parseWithKind(URI uri, String kind) {
            if (!URI_SCHEME.equals(uri.getScheme())) {
                throw new StoreException("Unsupported URI scheme: " + uri.getScheme());
            }

            if (uri.getPath() == null) {
                throw new StoreException("Missing URI path: " + uri);
            }

            var pathElements = uri.getPath().split("/");
            if (pathElements.length != 3) {
                throw new StoreException("Invalid URI path, expected /{orgName}/{projectName}/{repoName}: " + uri);
            }

            var orgName = uri.getAuthority();
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
            var formatOptions = parseFormatOptions(allowedFormats, queryParams);

            return new Query(
                    orgName,
                    projectName,
                    repoName,
                    ref,
                    kind,
                    path,
                    useFileNames,
                    namePrefix,
                    allowedFormats,
                    formatOptions,
                    0);
        }
    }
}
