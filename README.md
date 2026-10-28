# Weather Anomaly Detection System

## Project overview

This Java console application gets a five-day weather forecast from the OpenWeatherMap API and checks it for unusual weather. It groups the provider's three-hour forecast readings into daily summaries, applies simple rules, and prints an explanation for each flagged day. A CSV option is also included for offline demonstrations.

The project is intended for learning OOP and API use. Its thresholds are examples, not official weather warnings or a scientific climate model.

## Features

- Looks up a place by city name, then fetches its five-day forecast using an API key.
- Reads the key from the `OPENWEATHER_API_KEY` environment variable; it is not saved in the source code.
- Checks for high or low temperatures, heavy rainfall, strong wind, and sudden daily temperature changes.
- Prints the daily readings, anomaly explanations, and a summary.
- Can also read the included CSV sample or a CSV file of your own.

## OOP concepts

- **Encapsulation:** `WeatherRecord` and `Anomaly` keep their fields private and expose getters.
- **Abstraction:** `OpenWeatherMapForecastReader` handles API access and response parsing; `CsvWeatherDataReader` handles CSV input; `AnomalyDetector` applies rules; `ReportPrinter` displays results.
- **Composition:** `DetectionResult` contains a weather record and any anomalies found for it.
- **Object collaboration:** the main class coordinates input, detection, and reporting objects.

## Requirements

- Java 11 or newer (Java 11 includes the HTTP client used by the project).
- An OpenWeatherMap account and API key for live forecast mode. API key activation and provider limits are controlled by OpenWeatherMap.
- Internet access for live forecast mode.
- No extra Java libraries.

## Compile

Open PowerShell in the project folder and compile the Java source files:

```powershell
javac -d out src\main\java\weather\*.java
```

If `javac` is not on PATH but you use the Java runtime bundled with Minecraft Launcher, you can use its compiler directly:

```powershell
$javaBin = "$env:APPDATA\.minecraft\runtime\java-runtime-epsilon\windows\java-runtime-epsilon\bin"
& "$javaBin\javac.exe" -d out src\main\java\weather\*.java
```

## Run with interactive Web Interface (Recommended)

Launch the built-in pure Java web server (zero external dependencies):

```powershell
java -cp out weather.WeatherAnomalyDetectionSystem --web
```

Then open your browser to:
**[http://localhost:8080](http://localhost:8080)**

Features in the web interface:
- **Atmospheric Liquid Canvas:** Interactive WebGL 2D fluid simulation with mouse wake physics and state-reactive color shifts.
- **Horizon Spline & Precipitation Chart:** Interactive SVG visualization showing temperature trajectory, threshold limits, and rainfall volumes.
- **Dynamic Rule Calibration:** Live sliders to re-evaluate the 5 anomaly detection thresholds in real time.
- **Dual Observation Modes:** Switch between live OpenWeatherMap polling and local/uploaded CSV dataset analysis.

### Web Server REST Endpoints
| Endpoint | Method | Description |
|---|---|---|
| `/` | `GET` | Serves the atmospheric telemetry web client (`index.html`, `styles.css`, `app.js`). |
| `/api/sample` | `GET` | Evaluates the bundled `data/sample-weather.csv` with active threshold query parameters. |
| `/api/forecast` | `GET` | Fetches a 5-day forecast for `?city=City,Country` and returns computed anomaly telemetry JSON. |
| `/api/csv` | `POST` | Accepts raw CSV text in the request body and returns evaluated detection JSON. |

## Run with live API data (CLI)

Set your own OpenWeatherMap API key in the current PowerShell window. Replace the example text with your key; do not share it or add it to a project file:

```powershell
$env:OPENWEATHER_API_KEY = "paste-your-api-key-here"
```

Then run the app. Replace `London,GB` with your city and two-letter country code when useful:

```powershell
java -cp out weather.WeatherAnomalyDetectionSystem London,GB
```

If you use Minecraft's bundled Java, run it this way instead:

```powershell
& "$javaBin\java.exe" -cp out weather.WeatherAnomalyDetectionSystem London,GB
```

If you omit the city, the program asks for it. The app uses OpenWeatherMap's geocoding endpoint to find the city's coordinates, then requests the five-day, three-hour forecast in metric units. The provider returns forecast conditions, not historical observations; this project therefore checks unusual forecast values and changes between forecast days.

## Run with CSV data (offline)

```powershell
java -cp out weather.WeatherAnomalyDetectionSystem --csv data\sample-weather.csv
```

For another CSV, replace the sample path with the path to your file.

## CSV format

The first row may be a header. Columns must be in this order, with dates written as `YYYY-MM-DD`:

```csv
date,temperature,rainfall,windSpeed
2026-06-01,29.0,4.0,12.0
```

Values use Celsius, millimetres, and kilometres per hour.

## Detection rules

| Measurement | Rule |
|---|---|
| Temperature | Daily average above 40°C or below 0°C |
| Rainfall | Daily total above 100 mm |
| Wind speed | Daily maximum above 80 km/h |
| Temperature change | Daily average changes by more than 10°C from the previous calendar day |

These example values are set in `WeatherAnomalyDetectionSystem.java`; adjust them for your chosen location and project needs. The first forecast day may cover only part of a day, because forecasts begin from the current time. Daily dates are grouped in UTC.

## Project structure

```text
├── data/sample-weather.csv
├── src/main/java/weather/
│   ├── Anomaly.java
│   ├── AnomalyDetector.java
│   ├── CsvWeatherDataReader.java
│   ├── DetectionResult.java
│   ├── OpenWeatherMapForecastReader.java
│   ├── ReportPrinter.java
│   ├── WeatherAnomalyDetectionSystem.java
│   └── WeatherRecord.java
└── README.md
```

## Mini-project report outline

1. **Problem statement:** Manually reviewing weather readings makes it harder to spot unusual values.
2. **Objective:** Retrieve forecast readings from an API and report values outside selected limits.
3. **Method:** Request a forecast using an API key, convert forecast entries into daily weather objects, apply threshold and day-to-day rules, and print a report.
4. **Limitations:** Fixed thresholds can produce false alarms; forecasts are estimates and this version does not compare against long-term local climate data.
5. **Future improvements:** Add location-specific thresholds, historical datasets, charts, a graphical interface, and saved reports.
