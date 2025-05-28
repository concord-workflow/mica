package ca.ibodrov.mica.server;

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

import ca.ibodrov.mica.server.data.EntityStore;
import ca.ibodrov.mica.server.data.InitialDataLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class AbstractDatabaseTest {

    private static TestDatabase testDatabase;
    protected static ObjectMapper objectMapper;
    protected static UuidGenerator uuidGenerator;
    protected static EntityStore entityStore;

    @BeforeAll
    public static void setUpDatabase() {
        testDatabase = new TestDatabase();
        testDatabase.start();

        objectMapper = new ObjectMapperProvider().get();

        uuidGenerator = new UuidGenerator();

        var dsl = testDatabase.getJooqConfiguration().dsl();

        entityStore = new EntityStore(dsl, objectMapper, uuidGenerator);

        new InitialDataLoader(dsl, entityStore, objectMapper).load();
    }

    @AfterAll
    public static void tearDownDatabase() throws Exception {
        testDatabase.close();
    }

    protected static DSLContext dsl() {
        return testDatabase.getJooqConfiguration().dsl();
    }
}
