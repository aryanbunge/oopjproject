package weather;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvWeatherDataReaderTest {

    @Test
    @DisplayName("Should parse valid weather CSV rows")
    void parsesValidCsv(@TempDir Path tempDir) throws IOException {
        Path csvFile = tempDir.resolve("test-weather.csv");
        String content = """
                date,temperature,rainfall,wind_speed
                2026-06-01,25.5,12.0,18.0
                2026-06-02,30.0,0.0,22.5
                """;
        Files.writeString(csvFile, content);

        CsvWeatherDataReader reader = new CsvWeatherDataReader();
        List<WeatherRecord> records = reader.read(csvFile);

        assertEquals(2, records.size());
        assertEquals(LocalDate.of(2026, 6, 1), records.get(0).getDate());
        assertEquals(25.5, records.get(0).getTemperatureCelsius());
        assertEquals(12.0, records.get(0).getRainfallMm());
        assertEquals(18.0, records.get(0).getWindSpeedKmh());
    }

    @Test
    @DisplayName("Should throw exception when CSV file does not exist")
    void throwsOnMissingFile() {
        CsvWeatherDataReader reader = new CsvWeatherDataReader();
        assertThrows(IOException.class, () -> reader.read(Path.of("non-existent-file.csv")));
    }
}
