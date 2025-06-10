package ca.ibodrov.mica.server.data.js;

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

import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.graalvm.polyglot.*;
import org.graalvm.polyglot.io.IOAccess;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.requireNonNull;

public class GraalJsEvaluator implements JsEvaluator {

    private static final TypeReference<List<JsonNode>> LIST_OF_JSON_NODES = new TypeReference<List<JsonNode>>() {
    };

    private final ObjectMapper objectMapper;
    private final Executor executor;

    @Inject
    public GraalJsEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = requireNonNull(objectMapper);
        this.executor = Executors.newFixedThreadPool(32, new ThreadFactory() {

            private final AtomicLong id = new AtomicLong(0);

            @Override
            public Thread newThread(@NotNull Runnable r) {
                return new Thread(r, "js-" + id.getAndIncrement());
            }
        });
    }

    @Override
    public List<JsonNode> eval(String js, List<JsonNode> data) {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");

        var hostAccess = HostAccess.newBuilder()
                .allowPublicAccess(false)
                .allowAllImplementations(false)
                .allowAllClassImplementations(false)
                .build();

        var limits = ResourceLimits.newBuilder()
                .statementLimit(65536, null)
                .build();

        try (var context = Context.newBuilder("js")
                .allowHostAccess(hostAccess)
                .resourceLimits(limits)
                .allowIO(IOAccess.NONE)
                .allowCreateThread(false)
                .allowNativeAccess(false)
                .allowHostClassLookup(className -> {
                    System.out.println("!" + className);
                    return false;
                })
                .build()) {

            var jsData = convertToJs(context, objectMapper, data);
            context.getBindings("js").putMember("_input", jsData);

            var future = CompletableFuture.supplyAsync(() -> context.eval("js", js), executor);
            var result = future.get(10, TimeUnit.SECONDS);

            return convertToJava(context, objectMapper, result);
        } catch (TimeoutException e) {
            throw new StoreException("Timeout while executing the 'js' operation. Took more than 10 seconds.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StoreException("Interrupted");
        } catch (Exception e) {
            Throwable t = e;
            if (e.getCause() instanceof PolyglotException pe) {
                t = pe;
            }
            throw new StoreException("Error while executing the 'js' operation: " + t.getMessage());
        }
    }

    private static Value convertToJs(Context context, ObjectMapper objectMapper, List<JsonNode> data)
            throws IOException {
        var json = objectMapper.writeValueAsString(data);
        context.getBindings("js").putMember("_json", json);
        return context.eval("js", "JSON.parse(_json)");
    }

    private static List<JsonNode> convertToJava(Context context, ObjectMapper objectMapper, Value v)
            throws IOException {
        context.getBindings("js").putMember("_input", v);
        var result = context.eval("js", "JSON.stringify(_input)");
        return objectMapper.readValue(result.asString(), LIST_OF_JSON_NODES);
    }
}
