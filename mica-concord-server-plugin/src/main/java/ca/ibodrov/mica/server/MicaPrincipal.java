package ca.ibodrov.mica.server;

import java.io.Serial;
import java.io.Serializable;

public record MicaPrincipal(String username) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
