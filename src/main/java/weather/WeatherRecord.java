package weather;

import java.time.LocalDate;

/** One day's observed weather measurements. */
public class WeatherRecord {
    private final LocalDate date;
    private final double temperatureCelsius;
    private final double rainfallMm;
    private final double windSpeedKmh;
    private final double minTemperatureCelsius;
    private final double maxTemperatureCelsius;

    public WeatherRecord(LocalDate date, double temperatureCelsius, double rainfallMm, double windSpeedKmh) {
        this(date, temperatureCelsius, rainfallMm, windSpeedKmh, temperatureCelsius, temperatureCelsius);
    }

    public WeatherRecord(LocalDate date, double temperatureCelsius, double rainfallMm, double windSpeedKmh,
                         double minTemperatureCelsius, double maxTemperatureCelsius) {
        if (date == null) throw new IllegalArgumentException("Date is required.");
        if (rainfallMm < 0 || windSpeedKmh < 0) {
            throw new IllegalArgumentException("Rainfall and wind speed cannot be negative.");
        }
        this.date = date;
        this.temperatureCelsius = temperatureCelsius;
        this.rainfallMm = rainfallMm;
        this.windSpeedKmh = windSpeedKmh;
        this.minTemperatureCelsius = minTemperatureCelsius;
        this.maxTemperatureCelsius = maxTemperatureCelsius;
    }

    public LocalDate getDate() { return date; }
    public double getTemperatureCelsius() { return temperatureCelsius; }
    public double getRainfallMm() { return rainfallMm; }
    public double getWindSpeedKmh() { return windSpeedKmh; }
    public double getMinTemperatureCelsius() { return minTemperatureCelsius; }
    public double getMaxTemperatureCelsius() { return maxTemperatureCelsius; }
}
