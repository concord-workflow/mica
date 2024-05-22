package ca.ibodrov.mica.server.api;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.server.reports.ValidateAllReport;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Tag(name = "Report")
@Path("/api/mica/v1/report")
@Produces(APPLICATION_JSON)
public class ReportResource implements Resource {

    private final ValidateAllReport validateAll;

    @Inject
    public ReportResource(ValidateAllReport validateAll) {
        this.validateAll = requireNonNull(validateAll);
    }

    @POST
    @Path("validateAll")
    @Consumes(APPLICATION_JSON)
    @Operation(summary = "Validate all entities", operationId = "validateAll")
    public EntityLike validateAll(ValidateAllReport.Options options) {
        return validateAll.run(options);
    }
}
