package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectRepositoryManager;
import com.walmartlabs.concord.server.repository.RepositoryManager;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class ConcordRepositoryEntityFetcher implements EntityFetcher {

    private final OrganizationManager orgManager;
    private final ProjectRepositoryManager projectRepositoryManager;
    private final RepositoryManager repositoryManager;
    private final ObjectMapper yamlMapper;

    @Inject
    public ConcordRepositoryEntityFetcher(OrganizationManager orgManager,
                                          ProjectRepositoryManager projectRepositoryManager,
                                          RepositoryManager repositoryManager,
                                          ObjectMapper objectMapper) {

        this.orgManager = requireNonNull(orgManager);
        this.projectRepositoryManager = requireNonNull(projectRepositoryManager);
        this.repositoryManager = requireNonNull(repositoryManager);
        this.yamlMapper = requireNonNull(objectMapper).copyWith(new YAMLFactory());
    }

    @Override
    public Stream<EntityLike> getAllByKind(URI uri, String kind) {
        var orgName = uri.getHost();
        var projectName = uri.getPath().split("/")[1];
        var repoName = uri.getPath().split("/")[2];
        var path = uri.getQuery(); // TODO fix
        return getAllByKind(orgName, projectName, repoName, kind, path);
    }

    private Stream<EntityLike> getAllByKind(String orgName,
                                            String projectName,
                                            String repoName,
                                            String kind,
                                            String path) {

        var org = orgManager.assertAccess(orgName, false);
        var repoEntry = projectRepositoryManager.get(org.getId(), projectName, repoName);
        var repo = repositoryManager.fetch(repoEntry.getProjectId(), repoEntry);
        var basePath = repo.path().resolve(path);
        try {
            // noinspection Convert2MethodRef,resource
            return Files.walk(repo.path())
                    .filter(p -> p.startsWith(basePath))
                    .filter(p -> isMicaYamlFile(p))
                    .filter(p -> isOfValidKind(p, kind))
                    .map(this::parseFile);
        } catch (IOException e) {
            throw new StoreException("Error while reading the repository: " + e.getMessage(), e);
        }
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
}
