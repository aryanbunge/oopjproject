package weather;

import java.util.ArrayList;
import java.util.List;

/** Applies simple, explainable threshold and day-to-day change rules. */
public class AnomalyDetector {
    private final double maxTemperatureC;
    private final double minTemperatureC;
    private final double maxRainfallMm;
    private final double maxWindSpeedKmh;
    private final double maxDailyTemperatureChangeC;

    public AnomalyDetector(double maxTemperatureC, double minTemperatureC, double maxRainfallMm,
                           double maxWindSpeedKmh, double maxDailyTemperatureChangeC) {
        this.maxTemperatureC = maxTemperatureC;
        this.minTemperatureC = minTemperatureC;
        this.maxRainfallMm = maxRainfallMm;
        this.maxWindSpeedKmh = maxWindSpeedKmh;
        this.maxDailyTemperatureChangeC = maxDailyTemperatureChangeC;
    }

    public List<DetectionResult> detect(List<WeatherRecord> records) {
        List<DetectionResult> results = new ArrayList<>();
        WeatherRecord previous = null;
        for (WeatherRecord record : records) {
            DetectionResult result = new DetectionResult(record);
            double temperature = record.getTemperatureCelsius();
            if (temperature > maxTemperatureC) {
                result.addAnomaly(new Anomaly("High temperature", String.format("%.1f°C is above %.1f°C", temperature, maxTemperatureC)));
            }
            if (temperature < minTemperatureC) {
                result.addAnomaly(new Anomaly("Low temperature", String.format("%.1f°C is below %.1f°C", temperature, minTemperatureC)));
            }
            if (record.getRainfallMm() > maxRainfallMm) {
                result.addAnomaly(new Anomaly("Heavy rainfall", String.format("%.1f mm is above %.1f mm", record.getRainfallMm(), maxRainfallMm)));
            }
            if (record.getWindSpeedKmh() > maxWindSpeedKmh) {
                result.addAnomaly(new Anomaly("Strong wind", String.format("%.1f km/h is above %.1f km/h", record.getWindSpeedKmh(), maxWindSpeedKmh)));
            }
            if (previous != null && previous.getDate().plusDays(1).equals(record.getDate())) {
                double change = Math.abs(temperature - previous.getTemperatureCelsius());
                if (change > maxDailyTemperatureChangeC) {
                    result.addAnomaly(new Anomaly("Sudden temperature change",
                            String.format("temperature changed by %.1f°C since the previous day", change)));
                }
            }
            results.add(result);
            previous = record;
        }
        return results;
    }
}
