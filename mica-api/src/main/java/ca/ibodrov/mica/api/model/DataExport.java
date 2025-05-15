package ca.ibodrov.mica.api.model;

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
