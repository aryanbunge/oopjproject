package weather;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reads the project's simple four-column CSV format. */
public class CsvWeatherDataReader {
    public List<WeatherRecord> read(Path file) throws IOException {
        List<WeatherRecord> records = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
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
                    records.add(new WeatherRecord(LocalDate.parse(values[0].trim()),
                            Double.parseDouble(values[1].trim()), Double.parseDouble(values[2].trim()),
                            Double.parseDouble(values[3].trim())));
                } catch (RuntimeException exception) {
                    throw new IOException("Invalid weather data on line " + lineNumber + ": " + exception.getMessage(), exception);
                }
            }
        }
        records.sort(Comparator.comparing(WeatherRecord::getDate));
        return records;
    }
}
