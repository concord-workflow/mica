package ca.ibodrov.mica.server.ui;

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

import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.org.team.TeamRole;
import com.walmartlabs.concord.server.plugins.oidc.OidcToken;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.security.SecurityUtils;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.apache.shiro.util.ThreadContext;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.Roles.ROLES;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.UserTeams.USER_TEAMS;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/api/mica/ui/whoami")
public class WhoamiResource implements Resource {

    private final DSLContext dsl;

    @Inject
    public WhoamiResource(@MainDB Configuration cfg) {
        this.dsl = requireNonNull(cfg).dsl();
    }

    @GET
    @Produces(APPLICATION_JSON)
    @WithTimer
    public WhoamiResponse whoami(@Context UserPrincipal session) {
        var userId = session.getId();
        var oidcGroups = getOidcGroups().map(Set::copyOf).orElseGet(Set::of);
        var roles = getRoles();
        var teams = getTeams(userId);
        return new WhoamiResponse(userId, session.getUsername(), oidcGroups, roles, teams);
    }

    public record WhoamiResponse(UUID userId, String username, Set<String> oidcGroups, Set<String> roles,
            Set<UserTeam> teams) {
    }

    public record UserTeam(String orgName, String teamName, TeamRole teamRole) {
    }

    private Set<String> getRoles() {
        return dsl.select(ROLES.ROLE_NAME).from(ROLES).fetch(ROLES.ROLE_NAME)
                .stream().filter(role -> ThreadContext.getSubject().hasRole(role)).collect(toSet());
    }

    private Set<UserTeam> getTeams(UUID userId) {
        return dsl.select(ORGANIZATIONS.ORG_NAME, TEAMS.TEAM_NAME, USER_TEAMS.TEAM_ROLE)
                .from(USER_TEAMS)
                .join(TEAMS).on(TEAMS.TEAM_ID.eq(USER_TEAMS.TEAM_ID))
                .join(ORGANIZATIONS).on(ORGANIZATIONS.ORG_ID.eq(TEAMS.ORG_ID))
                .where(USER_TEAMS.USER_ID.eq(userId))
                .fetchSet(r -> new UserTeam(
                        r.get(ORGANIZATIONS.ORG_NAME),
                        r.get(TEAMS.TEAM_NAME),
                        TeamRole.valueOf(r.get(USER_TEAMS.TEAM_ROLE))));
    }

    @SuppressWarnings("unchecked")
    private static Optional<List<String>> getOidcGroups() {
        return Optional.ofNullable(SecurityUtils.getCurrent(OidcToken.class))
                .flatMap(t -> Optional.ofNullable((List<String>) t.getProfile().getAttribute("groups")));
    }
}
