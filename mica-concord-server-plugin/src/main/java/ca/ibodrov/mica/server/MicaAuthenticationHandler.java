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

import com.walmartlabs.concord.server.boot.filters.AuthenticationHandler;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import java.io.IOException;

public class MicaAuthenticationHandler implements AuthenticationHandler {

    @Override
    public boolean onAccessDenied(ServletRequest request, ServletResponse response) throws IOException {
        var req = (HttpServletRequest) request;
        var requestUri = req.getRequestURI();
        if (requestUri != null && requestUri.startsWith("/api/mica/")) {
            var resp = (HttpServletResponse) response;
            resp.setContentType(MediaType.APPLICATION_JSON);
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "{\"message\": \"Unauthorized\"}");
            return true;
        }

        return false;
    }
}
