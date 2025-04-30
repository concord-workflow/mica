package ca.ibodrov.mica.standalone;

import org.slf4j.bridge.SLF4JBridgeHandler;

public class Main {

    public static void main(String[] args) throws Exception {
        SLF4JBridgeHandler.install();

        var cfg = new Configuration()
                .configureServerPortUsingEnv()
                .configureSecretsUsingEnv()
                .configureOidcUsingEnv()
                .configureDatabaseUsingEnv()
                .configureDataDirUsingEnv()
                .toMap();

        try (var server = new MicaServer(cfg)) {
            server.start();

            System.out.println("""

                    MICA is ready.

                    """);
            Thread.currentThread().join();
        }
    }
}
