package ca.ibodrov.mica.server.ui;

import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.org.team.TeamRole;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.RoleEntry;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.UserTeams.USER_TEAMS;
import static java.util.Objects.requireNonNull;
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
    public WhoamiResponse whoami(@Context UserPrincipal session) {
        var userId = session.getId();
        var roles = session.getUser().getRoles().stream().map(RoleEntry::getName).toList();
        var teams = listTeams(userId);
        return new WhoamiResponse(userId, session.getUsername(), roles, teams);
    }

    public record WhoamiResponse(UUID userId, String username, List<String> roles, List<UserTeam> teams) {
    }

    public record UserTeam(String orgName, String teamName, TeamRole teamRole) {
    }

    private List<UserTeam> listTeams(UUID userId) {
        return dsl.select(ORGANIZATIONS.ORG_NAME, TEAMS.TEAM_NAME, USER_TEAMS.TEAM_ROLE)
                .from(USER_TEAMS)
                .join(TEAMS).on(TEAMS.TEAM_ID.eq(USER_TEAMS.TEAM_ID))
                .join(ORGANIZATIONS).on(ORGANIZATIONS.ORG_ID.eq(TEAMS.ORG_ID))
                .where(USER_TEAMS.USER_ID.eq(userId))
                .fetch(r -> new UserTeam(r.get(ORGANIZATIONS.ORG_NAME), r.get(TEAMS.TEAM_NAME),
                        TeamRole.valueOf(r.get(USER_TEAMS.TEAM_ROLE))));
    }
}
