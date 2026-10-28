package weather;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnomalyDetectorTest {

    @Test
    @DisplayName("Should detect no anomalies for weather within normal bounds")
    void detectsNoAnomaliesWithinBounds() {
        AnomalyDetector detector = new AnomalyDetector(40.0, 0.0, 100.0, 80.0, 10.0);
        WeatherRecord day1 = new WeatherRecord(LocalDate.of(2026, 6, 1), 25.0, 10.0, 20.0);
        WeatherRecord day2 = new WeatherRecord(LocalDate.of(2026, 6, 2), 27.0, 15.0, 25.0);

        List<DetectionResult> results = detector.detect(List.of(day1, day2));

        assertEquals(2, results.size());
        assertFalse(results.get(0).hasAnomalies());
        assertFalse(results.get(1).hasAnomalies());
    }

    @Test
    @DisplayName("Should detect high temperature, low temperature, heavy rainfall, and strong wind")
    void detectsThresholdBreaches() {
        AnomalyDetector detector = new AnomalyDetector(40.0, 0.0, 100.0, 80.0, 10.0);
        WeatherRecord highTemp = new WeatherRecord(LocalDate.of(2026, 6, 1), 45.0, 5.0, 10.0);
        WeatherRecord lowTemp = new WeatherRecord(LocalDate.of(2026, 6, 2), -5.0, 5.0, 10.0);
        WeatherRecord heavyRain = new WeatherRecord(LocalDate.of(2026, 6, 3), 20.0, 120.0, 10.0);
        WeatherRecord strongWind = new WeatherRecord(LocalDate.of(2026, 6, 4), 20.0, 5.0, 95.0);

        List<DetectionResult> results = detector.detect(List.of(highTemp, lowTemp, heavyRain, strongWind));

        assertTrue(results.get(0).hasAnomalies());
        assertEquals("High temperature", results.get(0).getAnomalies().get(0).getType());

        assertTrue(results.get(1).hasAnomalies());
        assertEquals("Low temperature", results.get(1).getAnomalies().get(0).getType());

        assertTrue(results.get(2).hasAnomalies());
        assertEquals("Heavy rainfall", results.get(2).getAnomalies().get(0).getType());

        assertTrue(results.get(3).hasAnomalies());
        assertEquals("Strong wind", results.get(3).getAnomalies().get(0).getType());
    }

    @Test
    @DisplayName("Should detect sudden day-over-day temperature changes")
    void detectsSuddenTemperatureChange() {
        AnomalyDetector detector = new AnomalyDetector(40.0, 0.0, 100.0, 80.0, 10.0);
        WeatherRecord day1 = new WeatherRecord(LocalDate.of(2026, 6, 1), 20.0, 5.0, 10.0);
        WeatherRecord day2 = new WeatherRecord(LocalDate.of(2026, 6, 2), 35.0, 5.0, 10.0); // +15 delta

        List<DetectionResult> results = detector.detect(List.of(day1, day2));

        assertFalse(results.get(0).hasAnomalies());
        assertTrue(results.get(1).hasAnomalies());
        assertTrue(results.get(1).getAnomalies().stream()
                .anyMatch(a -> a.getType().equals("Sudden temperature change")));
    }
}
