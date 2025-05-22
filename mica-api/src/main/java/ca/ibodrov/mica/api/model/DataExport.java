package ca.ibodrov.mica.api.model;

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

import java.util.List;
import java.util.Map;

public record DataExport(List<Map<String, Object>> concordOrganizations,
        List<Map<String, Object>> concordProjects,
        List<Map<String, Object>> concordRepositories,
        List<Map<String, Object>> concordSecrets,
        List<Map<String, Object>> concordProjectSecrets,
        List<Map<String, Object>> concordJsonStores,
        List<Map<String, Object>> concordJsonStoreQueries,
        List<Map<String, Object>> concordJsonStoreData,
        List<Map<String, Object>> concordUsers,
        List<Map<String, Object>> concordTeams,
        List<Map<String, Object>> concordProjectTeamAccess,
        List<Map<String, Object>> concordSecretTeamAccess,
        List<Map<String, Object>> concordJsonStoreTeamAccess,
        List<Map<String, Object>> concordApiKeys,
        List<Map<String, Object>> micaEntities,
        List<Map<String, Object>> micaEntityHistory) {
}
