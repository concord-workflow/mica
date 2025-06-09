package ca.ibodrov.mica.server.data;

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

import ca.ibodrov.mica.server.exceptions.StoreException;
import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.sdk.Secret;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import com.walmartlabs.concord.server.org.secret.SecretType;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;

import static com.walmartlabs.concord.server.org.secret.SecretManager.AccessScope.apiRequest;
import static java.util.Objects.requireNonNull;

public class ConcordSecretResolver {

    private final OrganizationManager orgManager;
    private final SecretManager secretManager;

    @Inject
    public ConcordSecretResolver(OrganizationManager orgManager, SecretManager secretManager) {
        this.orgManager = requireNonNull(orgManager);
        this.secretManager = requireNonNull(secretManager);
    }

    public Secret get(String secretRef, SecretType secretType) {
        secretRef = secretRef.trim();

        if (secretRef.isBlank()) {
            throw new StoreException("Invalid secretRef. Expected orgName/secretName format, got a blank value");
        }

        var idx = secretRef.indexOf("/");
        if (idx <= 0 || idx + 1 >= secretRef.length()) {
            throw new StoreException("Invalid secretRef. Expected orgName/secretName format, got: " + secretRef);
        }

        var orgName = secretRef.substring(0, idx);
        if (!orgName.matches(ConcordKey.PATTERN)) {
            throw new StoreException("Invalid secretRef. Expected an organization name, got: " + orgName);
        }

        var secretName = secretRef.substring(idx + 1);
        if (!secretName.matches(ConcordKey.PATTERN)) {
            throw new StoreException("Invalid secretRef. Expected a secret name, got: " + orgName);
        }

        try {
            var org = requireNonNull(orgManager).assertAccess(orgName, false);
            var secretContainer = requireNonNull(secretManager).getSecret(apiRequest(), org.getId(), secretName, null,
                    secretType);
            return secretContainer.getSecret();
        } catch (WebApplicationException e) {
            throw new StoreException("Can't fetch the secretRef " + secretRef + ". " + e.getMessage());
        }
    }
}
