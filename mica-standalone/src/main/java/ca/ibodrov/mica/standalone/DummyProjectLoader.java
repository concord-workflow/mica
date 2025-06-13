package ca.ibodrov.mica.standalone;

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

import com.walmartlabs.concord.imports.Import;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.imports.ImportsListener;
import com.walmartlabs.concord.process.loader.ImportsNormalizer;
import com.walmartlabs.concord.process.loader.ProjectLoader;
import com.walmartlabs.concord.repository.Snapshot;
import com.walmartlabs.concord.runtime.model.*;
import com.walmartlabs.concord.runtime.model.Configuration;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.walmartlabs.concord.process.loader.StandardRuntimeTypes.CONCORD_V2_RUNTIME_TYPE;

/**
 * Just a stub for the ProjectLoader. We don't need to parse repository data in
 * Mica.
 */
public class DummyProjectLoader implements ProjectLoader {

    @Override
    public boolean supports(String runtime) {
        return CONCORD_V2_RUNTIME_TYPE.equals(runtime);
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
