package ca.ibodrov.mica.concord.task;

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

import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.runtime.v2.sdk.Compiler;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

final class MockContext implements Context {

    private final String baseUrl;

    MockContext(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public Path workingDirectory() {
        return null;
    }

    @Override
    public UUID processInstanceId() {
        return null;
    }

    @Override
    public Variables variables() {
        return null;
    }

    @Override
    public Variables defaultVariables() {
        return new MapBackedVariables(Map.of());
    }

    @Override
    public FileService fileService() {
        return null;
    }

    @Override
    public DockerService dockerService() {
        return null;
    }

    @Override
    public SecretService secretService() {
        return null;
    }

    @Override
    public LockService lockService() {
        return null;
    }

    @Override
    public ApiConfiguration apiConfiguration() {
        return new ApiConfiguration() {
            @Override
            public String baseUrl() {
                return baseUrl;
            }

            @Override
            public int connectTimeout() {
                return 5000;
            }

            @Override
            public int readTimeout() {
                return 5000;
            }
        };
    }

    @Override
    public ProcessConfiguration processConfiguration() {
        return new ProcessConfiguration() {
            @Nullable
            @Override
            public UUID instanceId() {
                return null;
            }

            @Override
            public Map<String, Object> initiator() {
                return Map.of();
            }

            @Override
            public Map<String, Object> currentUser() {
                return Map.of();
            }

            @Override
            public ProcessInfo processInfo() {
                return new ProcessInfo() {
                    @Override
                    public String sessionToken() {
                        return "foobar";
                    }
                };
            }
        };
    }

    @Override
    public Execution execution() {
        return null;
    }

    @Override
    public Compiler compiler() {
        return null;
    }

    @Override
    public <T> T eval(Object v, Class<T> type) {
        return null;
    }

    @Override
    public <T> T eval(Object v, Map<String, Object> additionalVariables, Class<T> type) {
        return null;
    }

    @Override
    public void suspend(String eventName) {

    }

    @Override
    public void reentrantSuspend(String eventName, Map<String, Serializable> payload) {

    }
}
