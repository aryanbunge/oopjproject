package weather;

import java.util.List;

/** Prints a compact human-readable report to the console. */
public class ReportPrinter {
    public void print(List<DetectionResult> results, String title) {
        System.out.println("\nWEATHER ANOMALY DETECTION REPORT");
        System.out.println(title);
        System.out.println("=".repeat(76));
        System.out.printf("%-12s %10s %12s %12s  %s%n", "Date", "Temp (°C)", "Rain (mm)", "Wind (km/h)", "Status");
        int anomalyDays = 0;
        for (DetectionResult result : results) {
            WeatherRecord record = result.getRecord();
            String status = result.hasAnomalies() ? "ANOMALY" : "Normal";
            System.out.printf("%-12s %10.1f %12.1f %12.1f  %s%n", record.getDate(),
                    record.getTemperatureCelsius(), record.getRainfallMm(), record.getWindSpeedKmh(), status);
            for (Anomaly anomaly : result.getAnomalies()) {
                System.out.println("    - " + anomaly.getType() + ": " + anomaly.getExplanation());
            }
            if (result.hasAnomalies()) anomalyDays++;
        }
        System.out.println("=".repeat(76));
        System.out.printf("Days analyzed: %d | Days with anomalies: %d%n", results.size(), anomalyDays);
        if (anomalyDays == 0) System.out.println("No anomalies found.");
    }
}
