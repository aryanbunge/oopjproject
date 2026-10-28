package weather;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
public class WeatherAnomalyDetectionSystem {
    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(WeatherAnomalyDetectionSystem.class, args);
    }

    private static void loadDotEnv() {
        Path envFile = Path.of(".env");
        if (Files.exists(envFile)) {
            try (BufferedReader reader = Files.newBufferedReader(envFile)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        String key = line.substring(0, eq).trim();
                        String val = line.substring(eq + 1).trim();
                        if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                            val = val.substring(1, val.length() - 1);
                        }
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, val);
                        }
                    }
                }
            } catch (IOException ignored) {}
        }
    }
}
