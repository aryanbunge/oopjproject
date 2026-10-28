package weather;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP server exposing REST endpoints for the Weather Anomaly Detection System
 * and serving the atmospheric web frontend without external dependencies.
 */
public class WeatherWebServer {
    private static final int DEFAULT_PORT = 8080;
    private static final Path WEB_ROOT = Path.of("web");
    private static final Path SAMPLE_CSV = Path.of("data", "sample-weather.csv");

    private final HttpServer server;
    private final int port;

    public WeatherWebServer(int port) throws IOException {
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newCachedThreadPool());

        // Register HTTP routes
        this.server.createContext("/api/forecast", new ForecastHandler());
        this.server.createContext("/api/sample", new SampleHandler());
        this.server.createContext("/api/csv", new CsvHandler());
        this.server.createContext("/", new StaticFileHandler());
    }

    public void start() {
        server.start();
        System.out.println("============================================================================");
        System.out.println("  WEATHER ANOMALY DETECTION SYSTEM // WEB SERVER ACTIVE");
        System.out.println("  Local Telemetry URL: http://localhost:" + port + "/");
        System.out.println("  Sample Endpoint:     http://localhost:" + port + "/api/sample");
        System.out.println("  Forecast Endpoint:   http://localhost:" + port + "/api/forecast?city=London,GB");
        System.out.println("============================================================================");
    }

    public void stop(int delaySeconds) {
        server.stop(delaySeconds);
    }

    public static void startServer(int port) throws IOException {
        WeatherWebServer webServer = new WeatherWebServer(port > 0 ? port : DEFAULT_PORT);
        webServer.start();
    }

    // -------------------------------------------------------------------------
    // Route Handlers
    // -------------------------------------------------------------------------

    /** Handles GET /api/forecast */
    private static class ForecastHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (handleCorsPreflight(exchange)) return;

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed. Use GET.");
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI());
            String city = query.get("city");
            if (city == null || city.isBlank()) {
                sendError(exchange, 400, "City parameter is required (for example, ?city=London,GB).");
                return;
            }

            String apiKey = query.get("apiKey");
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = System.getenv("OPENWEATHER_API_KEY");
            }
            if (apiKey == null || apiKey.isBlank()) {
                sendError(exchange, 401, "OpenWeatherMap API key required. Provide 'apiKey' query param or set OPENWEATHER_API_KEY environment variable.");
                return;
            }

            Thresholds thresholds = Thresholds.fromQuery(query);

            try {
                OpenWeatherMapForecastReader reader = new OpenWeatherMapForecastReader();
                List<WeatherRecord> records = reader.readForecast(city.trim(), apiKey.trim());
                if (records.isEmpty()) {
                    sendError(exchange, 404, "No forecast records returned for city: " + city);
                    return;
                }

                AnomalyDetector detector = thresholds.toDetector();
                List<DetectionResult> results = detector.detect(records);
                String title = "OpenWeatherMap 5-day forecast for " + city.trim();

                String json = serializeResults(results, title, thresholds);
                sendJsonResponse(exchange, 200, json);
            } catch (IOException e) {
                int status = 500;
                String msg = e.getMessage();
                if (msg != null) {
                    if (msg.contains("rejected the key") || msg.contains("401")) status = 401;
                    else if (msg.contains("not found") || msg.contains("404")) status = 404;
                    else if (msg.contains("limit was reached") || msg.contains("429")) status = 429;
                }
                sendError(exchange, status, msg);
            } catch (RuntimeException e) {
                sendError(exchange, 500, "Internal error: " + e.getMessage());
            }
        }
    }

    /** Handles GET /api/sample */
    private static class SampleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (handleCorsPreflight(exchange)) return;

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed. Use GET.");
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI());
            Thresholds thresholds = Thresholds.fromQuery(query);

            if (!Files.exists(SAMPLE_CSV)) {
                sendError(exchange, 404, "Sample CSV file not found at " + SAMPLE_CSV);
                return;
            }

            try {
                CsvWeatherDataReader reader = new CsvWeatherDataReader();
                List<WeatherRecord> records = reader.read(SAMPLE_CSV);
                AnomalyDetector detector = thresholds.toDetector();
                List<DetectionResult> results = detector.detect(records);
                String title = "CSV data: " + SAMPLE_CSV;

                String json = serializeResults(results, title, thresholds);
                sendJsonResponse(exchange, 200, json);
            } catch (IOException e) {
                sendError(exchange, 500, "Could not read sample CSV: " + e.getMessage());
            }
        }
    }

    /** Handles POST /api/csv */
    private static class CsvHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (handleCorsPreflight(exchange)) return;

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed. Use POST with raw CSV body.");
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI());
            Thresholds thresholds = Thresholds.fromQuery(query);

            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
            String csvText = new String(bodyBytes, StandardCharsets.UTF_8);

            if (csvText.isBlank()) {
                sendError(exchange, 400, "Request body is empty. Please provide CSV weather data.");
                return;
            }

            try {
                List<WeatherRecord> records = parseCsvString(csvText);
                if (records.isEmpty()) {
                    sendError(exchange, 400, "No valid weather rows found in CSV data.");
                    return;
                }

                AnomalyDetector detector = thresholds.toDetector();
                List<DetectionResult> results = detector.detect(records);
                String title = "Custom Uploaded CSV Dataset";

                String json = serializeResults(results, title, thresholds);
                sendJsonResponse(exchange, 200, json);
            } catch (IOException | IllegalArgumentException e) {
                sendError(exchange, 400, "CSV Parsing Error: " + e.getMessage());
            }
        }
    }

    /** Handles static file serving: GET / (index.html, styles.css, app.js, etc.) */
    private static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (handleCorsPreflight(exchange)) return;

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            String rawPath = exchange.getRequestURI().getPath();
            if (rawPath == null || rawPath.equals("/")) {
                rawPath = "/index.html";
            }

            // Remove leading slashes and normalize to prevent directory traversal
            Path resolvedPath = WEB_ROOT.resolve(rawPath.replaceFirst("^/+", "")).normalize();

            if (!resolvedPath.startsWith(WEB_ROOT) || !Files.exists(resolvedPath) || Files.isDirectory(resolvedPath)) {
                sendError(exchange, 404, "Static file not found: " + rawPath);
                return;
            }

            String contentType = probeContentType(resolvedPath);
            byte[] fileBytes = Files.readAllBytes(resolvedPath);

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(fileBytes.length));
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");

            exchange.sendResponseHeaders(200, fileBytes.length);
            if (!"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(fileBytes);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helper Methods & Parsing
    // -------------------------------------------------------------------------

    private static List<WeatherRecord> parseCsvString(String csvContent) throws IOException {
        List<WeatherRecord> records = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;
                if (lineNumber == 1 && line.toLowerCase().startsWith("date,")) continue;
                String[] values = line.split(",", -1);
                if (values.length != 4) {
                    throw new IOException("Line " + lineNumber + " must contain date,temperature,rainfall,windSpeed.");
                }
                try {
                    records.add(new WeatherRecord(
                            LocalDate.parse(values[0].trim()),
                            Double.parseDouble(values[1].trim()),
                            Double.parseDouble(values[2].trim()),
                            Double.parseDouble(values[3].trim())
                    ));
                } catch (RuntimeException exception) {
                    throw new IOException("Invalid weather data on line " + lineNumber + ": " + exception.getMessage(), exception);
                }
            }
        }
        records.sort(Comparator.comparing(WeatherRecord::getDate));
        return records;
    }

    private static Map<String, String> parseQuery(URI uri) {
        Map<String, String> params = new HashMap<>();
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) return params;

        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            } else if (!pair.isBlank()) {
                params.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
            }
        }
        return params;
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static boolean handleCorsPreflight(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private static void sendJsonResponse(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        String safeMsg = message == null ? "Unknown error" : message;
        String json = "{\"error\":\"" + escapeJson(safeMsg) + "\"}";
        sendJsonResponse(exchange, status, json);
    }

    private static String probeContentType(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".html") || name.endsWith(".htm")) return "text/html; charset=UTF-8";
        if (name.endsWith(".css")) return "text/css; charset=UTF-8";
        if (name.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (name.endsWith(".json")) return "application/json; charset=UTF-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".ico")) return "image/x-icon";
        return "text/plain; charset=UTF-8";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < ' ') {
                        String hex = Integer.toHexString(ch);
                        sb.append("\\u").append("0000".substring(hex.length())).append(hex);
                    } else {
                        sb.append(ch);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // JSON Serialization
    // -------------------------------------------------------------------------

    private static String serializeResults(List<DetectionResult> results, String title, Thresholds thresholds) {
        int anomalyDays = 0;
        double peakTemp = -999.0;
        double lowestTemp = 999.0;
        double maxWind = 0.0;
        double totalRain = 0.0;

        for (DetectionResult r : results) {
            if (r.hasAnomalies()) anomalyDays++;
            WeatherRecord rec = r.getRecord();
            double temp = rec.getTemperatureCelsius();
            if (temp > peakTemp) peakTemp = temp;
            if (temp < lowestTemp) lowestTemp = temp;
            if (rec.getWindSpeedKmh() > maxWind) maxWind = rec.getWindSpeedKmh();
            totalRain += rec.getRainfallMm();
        }

        if (results.isEmpty()) {
            peakTemp = 0.0;
            lowestTemp = 0.0;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"title\": \"").append(escapeJson(title)).append("\",\n");
        sb.append("  \"thresholds\": {\n");
        sb.append("    \"maxTemperatureC\": ").append(thresholds.maxTemp).append(",\n");
        sb.append("    \"minTemperatureC\": ").append(thresholds.minTemp).append(",\n");
        sb.append("    \"maxRainfallMm\": ").append(thresholds.maxRain).append(",\n");
        sb.append("    \"maxWindSpeedKmh\": ").append(thresholds.maxWind).append(",\n");
        sb.append("    \"maxDailyTemperatureChangeC\": ").append(thresholds.maxDelta).append("\n");
        sb.append("  },\n");
        sb.append("  \"summary\": {\n");
        sb.append("    \"totalDays\": ").append(results.size()).append(",\n");
        sb.append("    \"anomalyDays\": ").append(anomalyDays).append(",\n");
        sb.append("    \"peakTemperatureC\": ").append(String.format("%.1f", peakTemp)).append(",\n");
        sb.append("    \"lowestTemperatureC\": ").append(String.format("%.1f", lowestTemp)).append(",\n");
        sb.append("    \"maxWindSpeedKmh\": ").append(String.format("%.1f", maxWind)).append(",\n");
        sb.append("    \"totalRainfallMm\": ").append(String.format("%.1f", totalRain)).append("\n");
        sb.append("  },\n");
        sb.append("  \"results\": [\n");

        for (int i = 0; i < results.size(); i++) {
            DetectionResult result = results.get(i);
            WeatherRecord record = result.getRecord();

            sb.append("    {\n");
            sb.append("      \"date\": \"").append(record.getDate()).append("\",\n");
            sb.append("      \"temperatureCelsius\": ").append(record.getTemperatureCelsius()).append(",\n");
            sb.append("      \"rainfallMm\": ").append(record.getRainfallMm()).append(",\n");
            sb.append("      \"windSpeedKmh\": ").append(record.getWindSpeedKmh()).append(",\n");
            sb.append("      \"hasAnomalies\": ").append(result.hasAnomalies()).append(",\n");
            sb.append("      \"status\": \"").append(result.hasAnomalies() ? "ANOMALY" : "NOMINAL").append("\",\n");
            sb.append("      \"anomalies\": [\n");

            List<Anomaly> anomalies = result.getAnomalies();
            for (int j = 0; j < anomalies.size(); j++) {
                Anomaly a = anomalies.get(j);
                sb.append("        {\n");
                sb.append("          \"type\": \"").append(escapeJson(a.getType())).append("\",\n");
                sb.append("          \"explanation\": \"").append(escapeJson(a.getExplanation())).append("\"\n");
                sb.append("        }").append(j < anomalies.size() - 1 ? "," : "").append("\n");
            }

            sb.append("      ]\n");
            sb.append("    }").append(i < results.size() - 1 ? "," : "").append("\n");
        }

        sb.append("  ]\n");
        sb.append("}\n");

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Threshold Value Holder
    // -------------------------------------------------------------------------

    private static class Thresholds {
        final double maxTemp;
        final double minTemp;
        final double maxRain;
        final double maxWind;
        final double maxDelta;

        Thresholds(double maxTemp, double minTemp, double maxRain, double maxWind, double maxDelta) {
            this.maxTemp = maxTemp;
            this.minTemp = minTemp;
            this.maxRain = maxRain;
            this.maxWind = maxWind;
            this.maxDelta = maxDelta;
        }

        static Thresholds fromQuery(Map<String, String> q) {
            double maxTemp = parseDouble(q.get("maxTemp"), 40.0);
            double minTemp = parseDouble(q.get("minTemp"), 0.0);
            double maxRain = parseDouble(q.get("maxRain"), 100.0);
            double maxWind = parseDouble(q.get("maxWind"), 80.0);
            double maxDelta = parseDouble(q.get("maxDelta"), 10.0);
            return new Thresholds(maxTemp, minTemp, maxRain, maxWind, maxDelta);
        }

        AnomalyDetector toDetector() {
            return new AnomalyDetector(maxTemp, minTemp, maxRain, maxWind, maxDelta);
        }

        private static double parseDouble(String str, double def) {
            if (str == null || str.isBlank()) return def;
            try { return Double.parseDouble(str.trim()); } catch (NumberFormatException e) { return def; }
        }
    }
}
