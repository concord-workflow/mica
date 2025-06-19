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

import ca.ibodrov.mica.api.model.ApiError;
import com.walmartlabs.concord.server.sdk.rest.Component;
import com.walmartlabs.concord.server.security.SecurityUtils;

import javax.annotation.Priority;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

import static javax.ws.rs.core.Response.Status.UNAUTHORIZED;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class ApiSecurityFilter implements ContainerRequestFilter, Component {

    @Context
    ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        var method = resourceInfo.getResourceMethod();
        var rolesAllowed = method.getAnnotation(RolesAllowed.class);
        if (rolesAllowed != null) {
            if (rolesAllowed.value() == null || rolesAllowed.value().length == 0) {
                throw new IllegalStateException("Empty @RolesAllowed on " + method);
            }

            var subject = SecurityUtils.getSubject();
            for (var role : rolesAllowed.value()) {
                if (subject.hasRole(role)) {
                    return;
                }
            }
            requestContext.abortWith(Response.status(UNAUTHORIZED)
                    .entity(ApiError.unauthorized("Unauthorized. Requires one of the following user roles: "
                            + String.join(", ", rolesAllowed.value())))
                    .build());
        }
    }
}
