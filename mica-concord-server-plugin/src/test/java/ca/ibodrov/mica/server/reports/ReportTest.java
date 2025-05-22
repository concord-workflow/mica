package ca.ibodrov.mica.server.reports;

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

import ca.ibodrov.mica.server.AbstractDatabaseTest;
import ca.ibodrov.mica.server.data.EntityKindStore;
import ca.ibodrov.mica.server.data.InternalEntityFetcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ReportTest extends AbstractDatabaseTest {

    private static final EntityKindStore entityKindStore = new EntityKindStore(entityStore);
    private static final InternalEntityFetcher internalEntityFetcher = new InternalEntityFetcher(dsl(), objectMapper);

    @Test
    public void runValidateAllReportOnInitialData() {
        var report = new ValidateAllReport(entityKindStore, internalEntityFetcher, objectMapper);
        var options = new ValidateAllReport.Options(true);
        var result = report.run(options).data().get("report");
        assertNotNull(result);
        assertTrue(result.isArray());
        // we expect no violations in our initial data (including examples)
        // if that's no longer true (we want "broken" examples), this test will fail
        // and needs to be updated
        assertEquals(0, result.size());
    }
}
