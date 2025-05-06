package ca.ibodrov.mica.standalone;

import com.walmartlabs.concord.imports.Import;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.imports.ImportsListener;
import com.walmartlabs.concord.process.loader.ImportsNormalizer;
import com.walmartlabs.concord.process.loader.ProjectLoader;
import com.walmartlabs.concord.process.loader.model.*;
import com.walmartlabs.concord.process.loader.model.Configuration;
import com.walmartlabs.concord.repository.Snapshot;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.walmartlabs.concord.process.loader.ConcordProjectLoader.CONCORD_V2_RUNTIME_TYPE;

/**
 * Just a stub for the ProjectLoader. We don't need to parse repository data in
 * Mica.
 */
public class DummyProjectLoader implements ProjectLoader {

    @Override
    public Result loadProject(Path path, ImportsNormalizer importsNormalizer, ImportsListener importsListener) {
        return new DummyResult();
    }

    @Override
    public Result loadProject(Path path,
                              String s,
                              ImportsNormalizer importsNormalizer,
                              ImportsListener importsListener) {
        return new DummyResult();
    }

    public static class DummyResult implements Result {

        @Override
        public List<Snapshot> snapshots() {
            return List.of();
        }

        @Override
        public ProcessDefinition projectDefinition() {
            return new ProcessDefinition() {
                @Override
                public String runtime() {
                    return CONCORD_V2_RUNTIME_TYPE;
                }

                @Override
                public Configuration configuration() {
                    return new Configuration() {
                        @Override
                        public Map<String, Object> asMap() {
                            return Map.of();
                        }

                        @Override
                        public List<String> dependencies() {
                            return List.of();
                        }

                        @Override
                        public List<String> extraDependencies() {
                            return List.of();
                        }
                    };
                }

                @Override
                public Map<String, FlowDefinition> flows() {
                    return Map.of();
                }

                @Override
                public Set<String> publicFlows() {
                    return Set.of();
                }

                @Override
                public Map<String, Profile> profiles() {
                    return Map.of();
                }

                @Override
                public List<Trigger> triggers() {
                    return List.of();
                }

                @Override
                public Imports imports() {
                    return new Imports() {
                        @Override
                        public List<Import> items() {
                            return List.of();
                        }

                        @Override
                        public boolean isEmpty() {
                            return true;
                        }
                    };
                }

                @Override
                public List<Form> forms() {
                    return List.of();
                }

                @Override
                public void serialize(Options options, OutputStream outputStream) {
                }
            };
        }
    }
}
