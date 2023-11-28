package ca.ibodrov.mica.its;

import java.io.IOException;
import java.util.Properties;

import static java.util.Objects.requireNonNull;

public class Version {

    private final String micaITsVersion;
    private final String gitVersion;

    public Version() {
        this.micaITsVersion = requireNonNull(
                loadProperty("/ca/ibodrov/mica/its/version.properties", "mica.its.version"));
        this.gitVersion = requireNonNull(loadProperty("/ca/ibodrov/mica/its/git.properties", "git.commit.id.describe"));
    }

    /**
     * Maven artifact version.
     */
    public String getMavenVersion() {
        return micaITsVersion;
    }

    /**
     * The release version of the Mica server.
     */
    public String getExpectedServerVersion() {
        return gitVersion;
    }

    private static String loadProperty(String path, String key) {
        var props = new Properties();
        try {
            props.load(Version.class.getResourceAsStream(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return props.getProperty(key);
    }
}
