package ca.ibodrov.mica.its;

import java.io.IOException;
import java.util.Properties;

import static java.util.Objects.requireNonNull;

public class Version {

    private final String micaITsVersion;

    public Version() {
        var props = new Properties();
        try {
            props.load(Version.class.getResourceAsStream("/ca/ibodrov/mica/its/version.properties"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.micaITsVersion = requireNonNull(props.getProperty("mica.its.version"));
    }

    public String getMicaITsVersion() {
        return micaITsVersion;
    }
}
