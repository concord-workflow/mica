package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.walmartlabs.concord.repository.FetchRequest.Version;
import com.walmartlabs.concord.repository.Repository;
import com.walmartlabs.concord.sdk.Secret;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectRepositoryManager;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import com.walmartlabs.concord.server.org.secret.SecretManager.AccessScope;
import com.walmartlabs.concord.server.repository.RepositoryManager;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;

public class ConcordGitEntityFetcher implements EntityFetcher {

    private static final String DEFAULT_REF = "main";

    private final OrganizationManager orgManager;
    private final ProjectRepositoryManager projectRepositoryManager;
    private final RepositoryManager repositoryManager;
    private final SecretManager secretManager;
    private final ObjectMapper yamlMapper;

    @Inject
    public ConcordGitEntityFetcher(OrganizationManager orgManager,
                                   ProjectRepositoryManager projectRepositoryManager,
                                   RepositoryManager repositoryManager,
                                   SecretManager secretManager,
                                   ObjectMapper objectMapper) {

        this.orgManager = requireNonNull(orgManager);
        this.projectRepositoryManager = requireNonNull(projectRepositoryManager);
        this.repositoryManager = requireNonNull(repositoryManager);
        this.secretManager = requireNonNull(secretManager);
        this.yamlMapper = requireNonNull(objectMapper).copyWith(new YAMLFactory());
    }

    @Override
    public List<EntityLike> getAllByKind(URI uri, String kind, int limit) {
        if (!uri.getScheme().equals("concord+git")) {
            return List.of();
        }

        var pathElements = uri.getPath().split("/");
        if (pathElements.length != 3) {
            throw new IllegalArgumentException("Invalid URI: " + uri);
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

        return getAllByKind(orgName, projectName, repoName, ref, kind, path, limit);
    }

    private List<EntityLike> getAllByKind(String orgName,
                                          String projectName,
                                          String repoName,
                                          String ref,
                                          String kind,
                                          String pathInRepo,
                                          int limit) {

        var org = orgManager.assertAccess(orgName, false);
        var repoEntry = projectRepositoryManager.get(org.getId(), projectName, repoName);
        var secret = Optional.ofNullable(repoEntry.getSecretName())
                .map(secretName -> getSecret(org.getId(), secretName));
        return repositoryManager.withLock(repoEntry.getUrl(), () -> {
            var result = fetch(repoEntry.getUrl(), ref, pathInRepo, secret.orElse(null));
            try {
                // noinspection Convert2MethodRef,resource
                return Files.walk(result.path())
                        .filter(p -> isMicaYamlFile(p))
                        .filter(p -> isOfValidKind(p, kind))
                        .limit(limit)
                        .map(this::parseFile)
                        .toList();
            } catch (IOException e) {
                throw new StoreException("Error while reading the repository: " + e.getMessage(), e);
            }
        });
    }

    private Secret getSecret(UUID orgId, String secretName) {
        return Optional.ofNullable(secretManager.getSecret(AccessScope.internal(), orgId, secretName, null, null))
                .orElseThrow(() -> new StoreException("Secret not found: " + secretName))
                .getSecret();
    }

    private Repository fetch(String url, String ref, String pathInRepo, Secret secret) {
        return repositoryManager.fetch(url, Version.from(ref), pathInRepo, secret, false);
    }

    private EntityLike parseFile(Path path) {
        try (var reader = Files.newBufferedReader(path, UTF_8)) {
            return yamlMapper.readValue(reader, PartialEntity.class);
        } catch (IOException e) {
            throw new StoreException("Error while reading %s: %s".formatted(path, e.getMessage()), e);
        }
    }

    private static boolean isMicaYamlFile(Path path) {
        var fileName = path.getFileName().toString();
        return fileName.endsWith(".yaml") || fileName.endsWith(".yml");
    }

    private static boolean isOfValidKind(Path path, String kind) {
        // a cheap way to check if the file is of the given kind
        try (var reader = Files.newBufferedReader(path, UTF_8)) {
            return reader.lines().anyMatch(l -> l.matches("kind:\\s+" + Pattern.quote(kind)));
        } catch (IOException e) {
            throw new StoreException("Error while reading %s: %s".formatted(path, e.getMessage()), e);
        }
    }

    public Map<String, List<String>> parseQueryParameters(String s) {
        if (s == null || s.isBlank()) {
            return Map.of();
        }
        return Arrays.stream(s.split("&"))
                .map(this::splitQueryParameter)
                .collect(groupingBy(Map.Entry::getKey, LinkedHashMap::new, mapping(Map.Entry::getValue, toList())));
    }

    public SimpleEntry<String, String> splitQueryParameter(String it) {
        var idx = it.indexOf("=");
        var key = idx > 0 ? it.substring(0, idx) : it;
        var value = idx > 0 && it.length() > idx + 1 ? it.substring(idx + 1) : null;
        return new SimpleEntry<>(
                URLDecoder.decode(key, UTF_8),
                value != null ? URLDecoder.decode(value, UTF_8) : null);
    }
}
