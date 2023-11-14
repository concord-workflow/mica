package ca.ibodrov.mica.server.oidc;

import java.io.Serial;
import java.io.Serializable;

public record OidcUserInfo(String email) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
