package ca.ibodrov.mica.its;

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
