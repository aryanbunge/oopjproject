package weather;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WeatherControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WeatherObservationRepository repository;

    @Test
    @DisplayName("GET / should forward to the web frontend index.html")
    void servesFrontendWelcomePage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Weather Anomaly Detector")));
    }

    @Test
    @DisplayName("GET /styles.css and /app.js should be available static resources")
    void servesStaticAssets() throws Exception {
        mockMvc.perform(get("/styles.css"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/sample returns detection results for sample dataset")
    void returnsSampleDetectionResults() throws Exception {
        mockMvc.perform(get("/api/sample"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", containsString("sample-weather.csv")))
                .andExpect(jsonPath("$.results", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.summary.totalDays", greaterThan(0)));
    }

    @Test
    @DisplayName("POST /api/csv processes custom CSV input")
    void processesCustomCsv() throws Exception {
        String csvContent = """
                date,temperature,rainfall,wind_speed
                2026-06-01,25.0,10.0,15.0
                2026-06-02,45.0,5.0,20.0
                """;

        mockMvc.perform(post("/api/csv")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(csvContent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[1].hasAnomalies", is(true)))
                .andExpect(jsonPath("$.results[1].status", is("Anomaly")));
    }

    @Test
    @DisplayName("H2 database persistence: saving observations and retrieving via /api/weather/history")
    void persistsAndRetrievesObservations() throws Exception {
        repository.save(new WeatherObservation(
                "TestCity",
                LocalDate.of(2026, 6, 10),
                28.0,
                5.0,
                14.0,
                false,
                "Normal"
        ));

        mockMvc.perform(get("/api/weather/history").param("city", "TestCity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].city", equalToIgnoringCase("TestCity")))
                .andExpect(jsonPath("$[0].averageTemperatureCelsius", is(28.0)));
    }

    @Test
    @DisplayName("GET /api/cities returns city suggestions for autocomplete")
    void returnsCitySuggestions() throws Exception {
        mockMvc.perform(get("/api/cities").param("q", "Lon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[0].name", containsStringIgnoringCase("Lon")));
    }
}
