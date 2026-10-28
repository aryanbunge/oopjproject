package weather;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WeatherAnalysisService {
    private final WeatherObservationRepository repository;
    private final OpenWeatherMapForecastReader forecastReader = new OpenWeatherMapForecastReader();
    private final AnomalyDetector detector = new AnomalyDetector(40.0, 0.0, 100.0, 80.0, 10.0);

    @Value("${openweather.api-key:}")
    private String apiKey;

    public WeatherAnalysisService(WeatherObservationRepository repository) {
        this.repository = repository;
    }

    public List<WeatherObservation> analyze(String city) {
        String key = (this.apiKey != null && !this.apiKey.isBlank()) ? this.apiKey.trim() : null;
        if (key == null) {
            String sys = System.getProperty("OPENWEATHER_API_KEY");
            if (sys != null && !sys.isBlank()) key = sys.trim();
        }
        if (key == null) {
            String env = System.getenv("OPENWEATHER_API_KEY");
            if (env != null && !env.isBlank()) key = env.trim();
        }
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Set the OPENWEATHER_API_KEY environment variable before requesting a live analysis.");
        }
        try {
            List<WeatherRecord> records = forecastReader.readForecast(city.trim(), key);
            List<DetectionResult> detections = detector.detect(records);
            List<WeatherObservation> saved = new ArrayList<>();
            for (DetectionResult detection : detections) {
                WeatherRecord record = detection.getRecord();
                String details = detection.getAnomalies().stream()
                        .map(anomaly -> anomaly.getType() + ": " + anomaly.getExplanation())
                        .reduce((first, next) -> first + "; " + next)
                        .orElse("No anomalies detected");
                saved.add(new WeatherObservation(city, record.getDate(), record.getTemperatureCelsius(),
                        record.getRainfallMm(), record.getWindSpeedKmh(), detection.hasAnomalies(), details));
            }
            return repository.saveAll(saved);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    public List<WeatherObservation> history(String city) {
        return repository.findByCityIgnoreCaseOrderByForecastDateDesc(city);
    }
}
