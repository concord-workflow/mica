package ca.ibodrov.mica.its;

import java.io.IOException;
import java.util.Properties;

import static java.util.Objects.requireNonNull;

public class Version {

    private final String mavenProjectVersion;
    private final String gitCommitId;

    public Version() {
        var projectProperties = loadProperties("/ca/ibodrov/mica/its/version.properties");
        this.mavenProjectVersion = requireNonNull(projectProperties.getProperty("project.version"));

        var gitProperties = loadProperties("/ca/ibodrov/mica/its/git.properties");
        this.gitCommitId = requireNonNull(gitProperties.getProperty("git.commit.id"));
    }

    public String getMavenProjectVersion() {
        return mavenProjectVersion;
    }

    public String getGitCommitId() {
        return gitCommitId;
    }

    private static Properties loadProperties(String path) {
        var props = new Properties();
        try {
            props.load(Version.class.getResourceAsStream(path));
            return props;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
