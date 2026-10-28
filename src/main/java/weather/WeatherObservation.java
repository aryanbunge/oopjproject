package weather;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.LocalDate;

/** A saved daily forecast summary and its anomaly result. */
@Entity
@Table(name = "weather_observations")
public class WeatherObservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String city;
    private LocalDate forecastDate;
    private double averageTemperatureCelsius;
    private double rainfallMm;
    private double maxWindSpeedKmh;
    private boolean anomaly;
    @Column(length = 2000)
    private String anomalyDetails;

    protected WeatherObservation() { }

    public WeatherObservation(String city, LocalDate forecastDate, double averageTemperatureCelsius,
                              double rainfallMm, double maxWindSpeedKmh, boolean anomaly, String anomalyDetails) {
        this.city = city;
        this.forecastDate = forecastDate;
        this.averageTemperatureCelsius = averageTemperatureCelsius;
        this.rainfallMm = rainfallMm;
        this.maxWindSpeedKmh = maxWindSpeedKmh;
        this.anomaly = anomaly;
        this.anomalyDetails = anomalyDetails;
    }

    public Long getId() { return id; }
    public String getCity() { return city; }
    public LocalDate getForecastDate() { return forecastDate; }
    public double getAverageTemperatureCelsius() { return averageTemperatureCelsius; }
    public double getRainfallMm() { return rainfallMm; }
    public double getMaxWindSpeedKmh() { return maxWindSpeedKmh; }
    public boolean isAnomaly() { return anomaly; }
    public String getAnomalyDetails() { return anomalyDetails; }
}
