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
import java.util.LinkedHashMap;
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

    /** Dedicated real-time current weather endpoint */
    @GetMapping("/api/current")
    public ResponseEntity<?> getCurrent(
            @RequestParam String city,
            @RequestParam(required = false) String apiKey) {
        String key = resolveApiKey(apiKey);
        if (key == null || key.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "API key required. Set OPENWEATHER_API_KEY in .env or provide apiKey param."));
        }
        try {
            OpenWeatherMapForecastReader reader = new OpenWeatherMapForecastReader();
            CurrentWeather cw = reader.readCurrentWeather(city.trim(), key);
            try {
                List<WeatherRecord> forecast = reader.readForecast(city.trim(), key);
                if (!forecast.isEmpty()) {
                    WeatherRecord today = forecast.get(0);
                    double minT = Math.min(cw.getTempMinCelsius(), today.getMinTemperatureCelsius());
                    double maxT = Math.max(cw.getTempMaxCelsius(), today.getMaxTemperatureCelsius());
                    minT = Math.min(minT, cw.getTemperatureCelsius());
                    maxT = Math.max(maxT, cw.getTemperatureCelsius());
                    cw = cw.withRange(minT, maxT);
                }
            } catch (Exception ignored) {}
            return ResponseEntity.ok(cw);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        }
    }

    /** City autocomplete suggestion endpoint using OpenWeather Geocoding + curated world cities */
    @GetMapping("/api/cities")
    public List<Map<String, String>> suggestCities(
            @RequestParam String q,
            @RequestParam(required = false) String apiKey) {
        String key = resolveApiKey(apiKey);
        List<Map<String, String>> results = new ArrayList<>();
        if (key != null && !key.isBlank()) {
            results.addAll(new OpenWeatherMapForecastReader().searchCities(q, key));
        }
        if (results.isEmpty() || results.size() < 3) {
            for (Map<String, String> city : filterCuratedCities(q)) {
                if (results.stream().noneMatch(c -> c.get("queryValue").equalsIgnoreCase(city.get("queryValue")))) {
                    results.add(city);
                }
            }
        }
        return results;
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
            OpenWeatherMapForecastReader reader = new OpenWeatherMapForecastReader();
            List<WeatherRecord> records = reader.readForecast(city.trim(), key);
            AnomalyDetector detector = new AnomalyDetector(maxTemp, minTemp, maxRain, maxWind, maxDelta);
            List<DetectionResult> results = detector.detect(records);

            // Fetch live current weather observation
            CurrentWeather currentWeather = null;
            try {
                currentWeather = reader.readCurrentWeather(city.trim(), key);
                if (currentWeather != null && !records.isEmpty()) {
                    WeatherRecord today = records.get(0);
                    double minT = Math.min(currentWeather.getTempMinCelsius(), today.getMinTemperatureCelsius());
                    double maxT = Math.max(currentWeather.getTempMaxCelsius(), today.getMaxTemperatureCelsius());
                    minT = Math.min(minT, currentWeather.getTemperatureCelsius());
                    maxT = Math.max(maxT, currentWeather.getTemperatureCelsius());
                    currentWeather = currentWeather.withRange(minT, maxT);
                }
            } catch (Exception ignored) {
                // If current weather fails, continue gracefully with forecast
            }

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

            return ResponseEntity.ok(formatResponse(results, "5-Day Forecast for " + city, maxTemp, minTemp, maxRain, maxWind, maxDelta, currentWeather));
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
        return formatResponse(results, title, maxT, minT, maxR, maxW, maxD, null);
    }

    private Map<String, Object> formatResponse(List<DetectionResult> results, String title, double maxT, double minT, double maxR, double maxW, double maxD, CurrentWeather currentWeather) {
        int anomalyDays = 0;
        double peak = -999, low = 999, maxWind = 0, totalRain = 0;
        List<Map<String, Object>> rows = new ArrayList<>();

        for (DetectionResult dr : results) {
            if (dr.hasAnomalies()) anomalyDays++;
            WeatherRecord wr = dr.getRecord();
            if (wr.getMaxTemperatureCelsius() > peak) peak = wr.getMaxTemperatureCelsius();
            if (wr.getMinTemperatureCelsius() < low) low = wr.getMinTemperatureCelsius();
            if (wr.getWindSpeedKmh() > maxWind) maxWind = wr.getWindSpeedKmh();
            totalRain += wr.getRainfallMm();

            List<Map<String, String>> anomalyList = new ArrayList<>();
            for (Anomaly a : dr.getAnomalies()) {
                anomalyList.add(Map.of("type", a.getType(), "explanation", a.getExplanation()));
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", wr.getDate().toString());
            row.put("temperatureCelsius", wr.getTemperatureCelsius());
            row.put("minTemperatureCelsius", wr.getMinTemperatureCelsius());
            row.put("maxTemperatureCelsius", wr.getMaxTemperatureCelsius());
            row.put("minTemp", wr.getMinTemperatureCelsius());
            row.put("maxTemp", wr.getMaxTemperatureCelsius());
            row.put("avgTemp", wr.getTemperatureCelsius());
            row.put("rainfallMm", wr.getRainfallMm());
            row.put("rainfall", wr.getRainfallMm());
            row.put("windSpeedKmh", wr.getWindSpeedKmh());
            row.put("windSpeed", wr.getWindSpeedKmh());
            row.put("hasAnomalies", dr.hasAnomalies());
            row.put("status", dr.hasAnomalies() ? "Anomaly" : "Normal");
            row.put("anomalies", anomalyList);
            rows.add(row);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("title", title);
        response.put("thresholds", Map.of("maxTemperatureC", maxT, "minTemperatureC", minT, "maxRainfallMm", maxR, "maxWindSpeedKmh", maxW, "maxDailyTemperatureChangeC", maxD));
        response.put("summary", Map.of("totalDays", results.size(), "anomalyDays", anomalyDays, "peakTemperatureC", String.format("%.1f", results.isEmpty() ? 0 : peak), "lowestTemperatureC", String.format("%.1f", results.isEmpty() ? 0 : low), "maxWindSpeedKmh", String.format("%.1f", maxWind), "totalRainfallMm", String.format("%.1f", totalRain)));
        response.put("results", rows);
        if (currentWeather != null) {
            response.put("currentWeather", currentWeather);
        }
        return response;
    }


    private List<Map<String, String>> filterCuratedCities(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }
        String q = query.trim().toLowerCase();
        List<Map<String, String>> all = List.of(
            Map.of("name", "London", "country", "GB", "state", "England", "displayName", "London, England, GB", "queryValue", "London,GB"),
            Map.of("name", "Tokyo", "country", "JP", "state", "Kanto", "displayName", "Tokyo, Kanto, JP", "queryValue", "Tokyo,JP"),
            Map.of("name", "New York", "country", "US", "state", "New York", "displayName", "New York, NY, US", "queryValue", "New York,US"),
            Map.of("name", "Mumbai", "country", "IN", "state", "Maharashtra", "displayName", "Mumbai, Maharashtra, IN", "queryValue", "Mumbai,IN"),
            Map.of("name", "Delhi", "country", "IN", "state", "Delhi", "displayName", "Delhi, IN", "queryValue", "Delhi,IN"),
            Map.of("name", "Bangalore", "country", "IN", "state", "Karnataka", "displayName", "Bangalore, Karnataka, IN", "queryValue", "Bangalore,IN"),
            Map.of("name", "Paris", "country", "FR", "state", "Ile-de-France", "displayName", "Paris, Ile-de-France, FR", "queryValue", "Paris,FR"),
            Map.of("name", "Berlin", "country", "DE", "state", "Berlin", "displayName", "Berlin, DE", "queryValue", "Berlin,DE"),
            Map.of("name", "Sydney", "country", "AU", "state", "New South Wales", "displayName", "Sydney, NSW, AU", "queryValue", "Sydney,AU"),
            Map.of("name", "Singapore", "country", "SG", "state", "", "displayName", "Singapore, SG", "queryValue", "Singapore,SG"),
            Map.of("name", "Dubai", "country", "AE", "state", "Dubai", "displayName", "Dubai, AE", "queryValue", "Dubai,AE"),
            Map.of("name", "Toronto", "country", "CA", "state", "Ontario", "displayName", "Toronto, ON, CA", "queryValue", "Toronto,CA"),
            Map.of("name", "San Francisco", "country", "US", "state", "California", "displayName", "San Francisco, CA, US", "queryValue", "San Francisco,US"),
            Map.of("name", "Chicago", "country", "US", "state", "Illinois", "displayName", "Chicago, IL, US", "queryValue", "Chicago,US"),
            Map.of("name", "Reykjavik", "country", "IS", "state", "", "displayName", "Reykjavik, IS", "queryValue", "Reykjavik,IS"),
            Map.of("name", "Rome", "country", "IT", "state", "Lazio", "displayName", "Rome, Lazio, IT", "queryValue", "Rome,IT"),
            Map.of("name", "Madrid", "country", "ES", "state", "Madrid", "displayName", "Madrid, ES", "queryValue", "Madrid,ES"),
            Map.of("name", "Amsterdam", "country", "NL", "state", "North Holland", "displayName", "Amsterdam, NL", "queryValue", "Amsterdam,NL"),
            Map.of("name", "Zurich", "country", "CH", "state", "Zurich", "displayName", "Zurich, CH", "queryValue", "Zurich,CH"),
            Map.of("name", "Seoul", "country", "KR", "state", "", "displayName", "Seoul, KR", "queryValue", "Seoul,KR")
        );
        List<Map<String, String>> matches = new ArrayList<>();
        for (Map<String, String> c : all) {
            if (c.get("name").toLowerCase().contains(q) || c.get("queryValue").toLowerCase().contains(q)) {
                matches.add(c);
            }
        }
        return matches;
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
