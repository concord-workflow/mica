package ca.ibodrov.mica.server.exceptions;

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

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

@Provider
public class StoreExceptionExceptionMapper implements Component, ExceptionMapper<StoreException> {

    @Override
    public Response toResponse(StoreException exception) {
        return Response.status(BAD_REQUEST)
                .type(APPLICATION_JSON)
                .entity(ApiError.badRequest(exception.getMessage()))
                .build();
    }
}
