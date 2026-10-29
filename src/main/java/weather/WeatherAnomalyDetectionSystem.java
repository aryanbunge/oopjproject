package weather;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@org.springframework.boot.autoconfigure.SpringBootApplication
public class WeatherAnomalyDetectionSystem {
    public static void main(String[] args) {
        if (args.length >= 2 && "--csv".equalsIgnoreCase(args[0])) {
            try {
                Path csvPath = Path.of(args[1]);
                List<WeatherRecord> records = new CsvWeatherDataReader().read(csvPath);
                AnomalyDetector detector = new AnomalyDetector(40.0, 0.0, 100.0, 80.0, 10.0);
                List<DetectionResult> results = detector.detect(records);
                new ReportPrinter().print(results, "Source: " + csvPath);
            } catch (Exception e) {
                System.err.println("Error reading CSV: " + e.getMessage());
            }
            return;
        }

        loadDotEnv();
        try {
            Class<?> springApp = Class.forName("org.springframework.boot.SpringApplication");
            java.lang.reflect.Method runMethod = springApp.getMethod("run", Class.class, String[].class);
            runMethod.invoke(null, WeatherAnomalyDetectionSystem.class, args);
        } catch (ClassNotFoundException e) {
            System.out.println("Spring Boot not on classpath. Starting zero-dependency WeatherWebServer on port 8080...");
            try {
                WeatherWebServer.startServer(8080);
            } catch (IOException ioException) {
                System.err.println("Could not start web server: " + ioException.getMessage());
            }
        } catch (Exception e) {
            System.err.println("Could not start application: " + e.getMessage());
        }
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
