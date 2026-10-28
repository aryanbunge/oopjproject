package weather;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {
    private final WeatherAnalysisService service;

    public WeatherController(WeatherAnalysisService service) {
        this.service = service;
    }

    /** Fetch a fresh five-day forecast, detect anomalies, save it, and return the saved report. */
    @GetMapping("/analyze")
    public List<WeatherObservation> analyze(@RequestParam String city) {
        return service.analyze(city);
    }

    /** Return previously saved analyses for a city without making an API request. */
    @GetMapping("/history")
    public List<WeatherObservation> history(@RequestParam String city) {
        return service.history(city);
    }
}
