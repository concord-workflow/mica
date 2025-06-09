package ca.ibodrov.mica.server.data.s3;

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

import ca.ibodrov.mica.server.data.ConcordSecretResolver;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.server.org.secret.SecretType;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import javax.inject.Inject;

import static java.util.Objects.requireNonNull;

public class ConcordSecretS3CredentialsProvider implements S3CredentialsProvider {

    private final ConcordSecretResolver usernamePasswordProvider;

    @Inject
    public ConcordSecretS3CredentialsProvider(ConcordSecretResolver usernamePasswordProvider) {
        this.usernamePasswordProvider = requireNonNull(usernamePasswordProvider);
    }

    @Override
    public StaticCredentialsProvider get(String secretRef) {
        var secret = usernamePasswordProvider.get(secretRef, SecretType.USERNAME_PASSWORD);
        if (secret instanceof UsernamePassword credentials) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(credentials.getUsername(), new String(credentials.getPassword())));
        } else {
            throw new StoreException(
                    "Invalid secretRef. Expected a username/password secret, got: " + secret.getClass());
        }
    }
}
