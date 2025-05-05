package ca.ibodrov.mica.standalone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

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
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Received SIGTERM, stopping the server...");
                try {
                    server.stop();
                } catch (Exception e) {
                    log.warn("Failed to stop the server graciously: {}", e.getMessage());
                }
            }, "shutdown-hook"));

            server.start();

            log.info("""

                    MICA is ready.

                    """);

            server.waitForStop();
        }

        log.info("Bye!");
    }
}
