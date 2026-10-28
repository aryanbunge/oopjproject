package weather;

import java.time.Instant;

/**
 * Encapsulates real-time meteorological observations for a specific station or city.
 * Follows strict Object-Oriented principles with immutable attributes and full encapsulation.
 */
public class CurrentWeather {
    private final String cityName;
    private final String country;
    private final double temperatureCelsius;
    private final double feelsLikeCelsius;
    private final double tempMinCelsius;
    private final double tempMaxCelsius;
    private final double humidityPercent;
    private final double windSpeedKmh;
    private final int windDegree;
    private final double pressureHpa;
    private final String condition;
    private final String description;
    private final String iconCode;
    private final Instant timestamp;

    public CurrentWeather(String cityName, String country, double temperatureCelsius,
                          double feelsLikeCelsius, double tempMinCelsius, double tempMaxCelsius,
                          double humidityPercent, double windSpeedKmh, int windDegree,
                          double pressureHpa, String condition, String description,
                          String iconCode, Instant timestamp) {
        this.cityName = cityName;
        this.country = country;
        this.temperatureCelsius = temperatureCelsius;
        this.feelsLikeCelsius = feelsLikeCelsius;
        this.tempMinCelsius = tempMinCelsius;
        this.tempMaxCelsius = tempMaxCelsius;
        this.humidityPercent = humidityPercent;
        this.windSpeedKmh = windSpeedKmh;
        this.windDegree = windDegree;
        this.pressureHpa = pressureHpa;
        this.condition = condition;
        this.description = description;
        this.iconCode = iconCode;
        this.timestamp = timestamp;
    }

    public String getCityName() {
        return cityName;
    }

    public String getCountry() {
        return country;
    }

    public double getTemperatureCelsius() {
        return temperatureCelsius;
    }

    public double getFeelsLikeCelsius() {
        return feelsLikeCelsius;
    }

    public double getTempMinCelsius() {
        return tempMinCelsius;
    }

    public double getTempMaxCelsius() {
        return tempMaxCelsius;
    }

    public double getHumidityPercent() {
        return humidityPercent;
    }

    public double getWindSpeedKmh() {
        return windSpeedKmh;
    }

    public int getWindDegree() {
        return windDegree;
    }

    public double getPressureHpa() {
        return pressureHpa;
    }

    public String getCondition() {
        return condition;
    }

    public String getDescription() {
        return description;
    }

    public String getIconCode() {
        return iconCode;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
