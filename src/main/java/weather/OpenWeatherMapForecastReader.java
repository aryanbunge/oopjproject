package weather;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Downloads the OpenWeatherMap 5-day/3-hour forecast and groups it by UTC day. */
public class OpenWeatherMapForecastReader {
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public List<WeatherRecord> readForecast(String city, String apiKey) throws IOException {
        String encodedCity = URLEncoder.encode(city.trim(), StandardCharsets.UTF_8);
        String encodedKey = URLEncoder.encode(apiKey.trim(), StandardCharsets.UTF_8);
        String geocodingJson = get("https://api.openweathermap.org/geo/1.0/direct?q=" + encodedCity
                + "&limit=1&appid=" + encodedKey);
        Matcher latitudeMatcher = Pattern.compile("\\\"lat\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(geocodingJson);
        Matcher longitudeMatcher = Pattern.compile("\\\"lon\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(geocodingJson);
        if (!latitudeMatcher.find() || !longitudeMatcher.find()) {
            throw new IOException("City not found. Try a city name with country code, such as London,GB.");
        }
        String forecastUrl = "https://api.openweathermap.org/data/2.5/forecast?lat=" + latitudeMatcher.group(1)
                + "&lon=" + longitudeMatcher.group(1) + "&appid=" + encodedKey + "&units=metric";
        return parseForecast(get(forecastUrl));
    }

    private String get(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        final HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("The API request was interrupted.", exception);
        }
        if (response.statusCode() != 200) {
            throw new IOException(describeApiError(response.statusCode(), response.body()));
        }
        return response.body();
    }

    private String describeApiError(int status, String body) {
        if (status == 401) return "OpenWeatherMap rejected the key. Check it is correct and active.";
        if (status == 404) return "Requested location was not found. Try a city and country code, such as London,GB.";
        if (status == 429) return "The API request limit was reached. Try again later.";
        return "OpenWeatherMap returned HTTP " + status + ". " + body;
    }

    private List<WeatherRecord> parseForecast(String json) throws IOException {
        int listStart = json.indexOf("\"list\"");
        int arrayStart = listStart < 0 ? -1 : json.indexOf('[', listStart);
        int arrayEnd = arrayStart < 0 ? -1 : findArrayEnd(json, arrayStart);
        if (arrayStart < 0 || arrayEnd < 0) throw new IOException("The weather API response did not contain a forecast list.");

        Map<LocalDate, DailyValues> days = new LinkedHashMap<>();
        int position = arrayStart + 1;
        while (position < arrayEnd) {
            int objectStart = json.indexOf('{', position);
            if (objectStart < 0 || objectStart >= arrayEnd) break;
            int objectEnd = findObjectEnd(json, objectStart);
            if (objectEnd < 0 || objectEnd > arrayEnd) throw new IOException("Could not read a forecast entry from the API response.");
            String entry = json.substring(objectStart, objectEnd + 1);
            addForecastEntry(days, entry);
            position = objectEnd + 1;
        }
        if (days.isEmpty()) throw new IOException("The weather API returned no usable forecast entries.");

        List<WeatherRecord> records = new ArrayList<>();
        for (Map.Entry<LocalDate, DailyValues> day : days.entrySet()) {
            DailyValues values = day.getValue();
            records.add(new WeatherRecord(day.getKey(), values.temperatureSum / values.count,
                    values.rainfallSum, values.maxWindSpeed));
        }
        return records;
    }

    private void addForecastEntry(Map<LocalDate, DailyValues> days, String entry) throws IOException {
        String dateText = stringValue(entry, "dt_txt");
        String mainObject = objectValue(entry, "main");
        String windObject = objectValue(entry, "wind");
        if (dateText == null || mainObject == null || windObject == null) return;
        try {
            LocalDate date = Instant.parse(dateText.replace(" ", "T") + "Z").atZone(ZoneOffset.UTC).toLocalDate();
            double temperature = numberValue(mainObject, "temp", true);
            double wind = numberValue(windObject, "speed", true);
            String rainObject = objectValue(entry, "rain");
            double rain = rainObject == null ? 0.0 : numberValue(rainObject, "3h", false);
            DailyValues values = days.computeIfAbsent(date, ignored -> new DailyValues());
            values.temperatureSum += temperature;
            values.count++;
            values.rainfallSum += rain;
            values.maxWindSpeed = Math.max(values.maxWindSpeed, wind);
        } catch (RuntimeException exception) {
            throw new IOException("Could not parse a forecast entry: " + exception.getMessage(), exception);
        }
    }

    private int findObjectEnd(String json, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char character = json.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (character == '\\') escaped = true;
                else if (character == '"') inString = false;
            } else if (character == '"') inString = true;
            else if (character == '{') depth++;
            else if (character == '}' && --depth == 0) return i;
        }
        return -1;
    }

    private int findArrayEnd(String json, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char character = json.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (character == '\\') escaped = true;
                else if (character == '"') inString = false;
            } else if (character == '"') inString = true;
            else if (character == '[') depth++;
            else if (character == ']' && --depth == 0) return i;
        }
        return -1;
    }

    private String objectValue(String source, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\{").matcher(source);
        if (!matcher.find()) return null;
        int start = matcher.end() - 1;
        int end = findObjectEnd(source, start);
        return end < 0 ? null : source.substring(start + 1, end);
    }

    private String stringValue(String source, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(source);
        return matcher.find() ? matcher.group(1) : null;
    }

    private double numberValue(String source, String key, boolean required) throws IOException {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(source);
        if (!matcher.find()) {
            if (required) throw new IOException("The API response did not include " + key + ".");
            return 0.0;
        }
        return Double.parseDouble(matcher.group(1));
    }

    private static class DailyValues {
        private double temperatureSum;
        private int count;
        private double rainfallSum;
        private double maxWindSpeed;
    }
}
