package weather;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class WeatherController {
    private final WeatherAnalysisService service;
    private final WeatherObservationRepository repository;
    private static final Path SAMPLE_CSV = Path.of("data", "sample-weather.csv");

    @org.springframework.beans.factory.annotation.Value("${openweather.api-key:}")
    private String configuredApiKey;

    public WeatherController(WeatherAnalysisService service, WeatherObservationRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    private String resolveApiKey(String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        if (configuredApiKey != null && !configuredApiKey.isBlank()) {
            return configuredApiKey.trim();
        }
        String sys = System.getProperty("OPENWEATHER_API_KEY");
        if (sys != null && !sys.isBlank()) {
            return sys.trim();
        }
        String env = System.getenv("OPENWEATHER_API_KEY");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return null;
    }

    /** Database analysis endpoint from main branch */
    @GetMapping("/api/weather/analyze")
    public List<WeatherObservation> analyze(@RequestParam String city) {
        return service.analyze(city);
    }

    /** Database history endpoint from main branch */
    @GetMapping("/api/weather/history")
    public List<WeatherObservation> history(@RequestParam String city) {
        return service.history(city);
    }

    /** Frontend endpoint: Live forecast + auto-persists to H2 database */
    @GetMapping("/api/forecast")
    public ResponseEntity<?> getForecast(
            @RequestParam String city,
            @RequestParam(required = false) String apiKey,
            @RequestParam(defaultValue = "40.0") double maxTemp,
            @RequestParam(defaultValue = "0.0") double minTemp,
            @RequestParam(defaultValue = "100.0") double maxRain,
            @RequestParam(defaultValue = "80.0") double maxWind,
            @RequestParam(defaultValue = "10.0") double maxDelta) {
        
        String key = resolveApiKey(apiKey);
        if (key == null || key.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "API key required. Set OPENWEATHER_API_KEY in .env or provide apiKey param."));
        }

        try {
            List<WeatherRecord> records = new OpenWeatherMapForecastReader().readForecast(city.trim(), key);
            AnomalyDetector detector = new AnomalyDetector(maxTemp, minTemp, maxRain, maxWind, maxDelta);
            List<DetectionResult> results = detector.detect(records);

            // Persist observations into H2 Database
            List<WeatherObservation> toSave = new ArrayList<>();
            for (DetectionResult dr : results) {
                WeatherRecord r = dr.getRecord();
                String details = dr.getAnomalies().stream()
                        .map(a -> a.getType() + ": " + a.getExplanation())
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("Normal");
                toSave.add(new WeatherObservation(city, r.getDate(), r.getTemperatureCelsius(),
                        r.getRainfallMm(), r.getWindSpeedKmh(), dr.hasAnomalies(), details));
            }
            repository.saveAll(toSave);

            return ResponseEntity.ok(formatResponse(results, "5-Day Forecast for " + city, maxTemp, minTemp, maxRain, maxWind, maxDelta));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        }
    }

    /** Frontend endpoint: sample CSV dataset */
    @GetMapping("/api/sample")
    public ResponseEntity<?> getSample(
            @RequestParam(defaultValue = "40.0") double maxTemp,
            @RequestParam(defaultValue = "0.0") double minTemp,
            @RequestParam(defaultValue = "100.0") double maxRain,
            @RequestParam(defaultValue = "80.0") double maxWind,
            @RequestParam(defaultValue = "10.0") double maxDelta) {
        try {
            List<WeatherRecord> records = new CsvWeatherDataReader().read(SAMPLE_CSV);
            AnomalyDetector detector = new AnomalyDetector(maxTemp, minTemp, maxRain, maxWind, maxDelta);
            return ResponseEntity.ok(formatResponse(detector.detect(records), "Sample Dataset: " + SAMPLE_CSV, maxTemp, minTemp, maxRain, maxWind, maxDelta));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /** Frontend endpoint: Upload raw CSV text */
    @PostMapping("/api/csv")
    public ResponseEntity<?> postCsv(
            @RequestBody String csvText,
            @RequestParam(defaultValue = "40.0") double maxTemp,
            @RequestParam(defaultValue = "0.0") double minTemp,
            @RequestParam(defaultValue = "100.0") double maxRain,
            @RequestParam(defaultValue = "80.0") double maxWind,
            @RequestParam(defaultValue = "10.0") double maxDelta) {
        try {
            List<WeatherRecord> records = parseCsv(csvText);
            AnomalyDetector detector = new AnomalyDetector(maxTemp, minTemp, maxRain, maxWind, maxDelta);
            return ResponseEntity.ok(formatResponse(detector.detect(records), "Custom Uploaded CSV", maxTemp, minTemp, maxRain, maxWind, maxDelta));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> formatResponse(List<DetectionResult> results, String title, double maxT, double minT, double maxR, double maxW, double maxD) {
        int anomalyDays = 0;
        double peak = -999, low = 999, maxWind = 0, totalRain = 0;
        List<Map<String, Object>> rows = new ArrayList<>();

        for (DetectionResult dr : results) {
            if (dr.hasAnomalies()) anomalyDays++;
            WeatherRecord wr = dr.getRecord();
            if (wr.getTemperatureCelsius() > peak) peak = wr.getTemperatureCelsius();
            if (wr.getTemperatureCelsius() < low) low = wr.getTemperatureCelsius();
            if (wr.getWindSpeedKmh() > maxWind) maxWind = wr.getWindSpeedKmh();
            totalRain += wr.getRainfallMm();

            List<Map<String, String>> anomalyList = new ArrayList<>();
            for (Anomaly a : dr.getAnomalies()) {
                anomalyList.add(Map.of("type", a.getType(), "explanation", a.getExplanation()));
            }

            rows.add(Map.of(
                "date", wr.getDate().toString(),
                "temperatureCelsius", wr.getTemperatureCelsius(),
                "rainfallMm", wr.getRainfallMm(),
                "windSpeedKmh", wr.getWindSpeedKmh(),
                "hasAnomalies", dr.hasAnomalies(),
                "status", dr.hasAnomalies() ? "Anomaly" : "Normal",
                "anomalies", anomalyList
            ));
        }

        return Map.of(
            "title", title,
            "thresholds", Map.of("maxTemperatureC", maxT, "minTemperatureC", minT, "maxRainfallMm", maxR, "maxWindSpeedKmh", maxW, "maxDailyTemperatureChangeC", maxD),
            "summary", Map.of("totalDays", results.size(), "anomalyDays", anomalyDays, "peakTemperatureC", String.format("%.1f", results.isEmpty() ? 0 : peak), "lowestTemperatureC", String.format("%.1f", results.isEmpty() ? 0 : low), "maxWindSpeedKmh", String.format("%.1f", maxWind), "totalRainfallMm", String.format("%.1f", totalRain)),
            "results", rows
        );
    }

    private List<WeatherRecord> parseCsv(String csv) throws IOException {
        List<WeatherRecord> records = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.isBlank() || (lineNum == 1 && line.toLowerCase().startsWith("date,"))) continue;
                String[] vals = line.split(",", -1);
                if (vals.length != 4) throw new IOException("Line " + lineNum + " must have 4 columns");
                records.add(new WeatherRecord(LocalDate.parse(vals[0].trim()), Double.parseDouble(vals[1].trim()), Double.parseDouble(vals[2].trim()), Double.parseDouble(vals[3].trim())));
            }
        }
        records.sort(Comparator.comparing(WeatherRecord::getDate));
        return records;
    }
}
