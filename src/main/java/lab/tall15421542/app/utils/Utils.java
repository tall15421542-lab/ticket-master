package lab.tall15421542.app.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class Utils {
    public static Properties readConfig(final String configFile) throws IOException {
        // reads the client configuration from client.properties
        // and returns it as a Properties object
        if (!Files.exists(Paths.get(configFile))) {
            throw new IOException(configFile + " not found.");
        }

        final Properties config = new Properties();
        try (InputStream inputStream = new FileInputStream(configFile)) {
            config.load(inputStream);
        }

        return config;
    }

    public static void addShutdownHookAndBlock(final ShutDownHook hook) throws InterruptedException {
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> hook.close());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                hook.close();
            } catch (final Exception ignored) {
            }
        }));
        Thread.currentThread().join();
    }
}
