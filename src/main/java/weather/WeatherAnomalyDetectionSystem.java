package weather;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

/** Entry point for the Weather Anomaly Detection System mini project. */
public class WeatherAnomalyDetectionSystem {
    public static void main(String[] args) {
        try {
            List<WeatherRecord> records;
            String reportTitle;
            if (args.length > 0 && args[0].equalsIgnoreCase("--csv")) {
                if (args.length < 2) {
                    System.err.println("CSV mode needs a file path: --csv path-to-weather.csv");
                    return;
                }
                Path inputFile = Path.of(args[1]);
                records = new CsvWeatherDataReader().read(inputFile);
                reportTitle = "CSV data: " + inputFile;
            } else {
                String apiKey = System.getenv("OPENWEATHER_API_KEY");
                if (apiKey == null || apiKey.isBlank()) {
                    System.err.println("Set your OpenWeatherMap key in OPENWEATHER_API_KEY first.");
                    System.err.println("PowerShell: $env:OPENWEATHER_API_KEY = \"your-key\"");
                    System.err.println("Then run: java -cp out weather.WeatherAnomalyDetectionSystem [city name]");
                    return;
                }
                String city = getCity(args);
                if (city == null || city.isBlank()) {
                    System.err.println("A city is required, for example: java -cp out weather.WeatherAnomalyDetectionSystem London,GB");
                    return;
                }
                records = new OpenWeatherMapForecastReader().readForecast(city, apiKey);
                reportTitle = "OpenWeatherMap 5-day forecast for " + city;
            }
            if (records.isEmpty()) {
                System.out.println("No weather records were returned.");
                return;
            }
            // These beginner-friendly thresholds can be changed for another region or season.
            AnomalyDetector detector = new AnomalyDetector(40.0, 0.0, 100.0, 80.0, 10.0);
            new ReportPrinter().print(detector.detect(records), reportTitle);
        } catch (IOException exception) {
            System.err.println("Could not get weather data: " + exception.getMessage());
        }
    }

    private static String getCity(String[] args) {
        if (args.length > 0) return String.join(" ", args);
        System.out.print("Enter a city (for example, London,GB): ");
        Scanner scanner = new Scanner(System.in);
        return scanner.hasNextLine() ? scanner.nextLine().trim() : "";
    }
}
