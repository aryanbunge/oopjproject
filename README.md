# Weather Anomaly Detection System

## Project overview

A beginner-friendly Java mini project that uses **Spring Boot**, a REST API, the OpenWeatherMap forecast API, and an **H2 database**. The app fetches a five-day forecast for a city, groups forecast readings into daily summaries, applies explainable anomaly rules, saves the analysis, and returns JSON.

The rules are examples for a college project. They are not official weather warnings or a scientific climate model.

## What it does

- **Spring Boot REST service:** exposes endpoints at `http://localhost:8080`.
- **Weather API:** resolves a city and retrieves its five-day, three-hour forecast from OpenWeatherMap.
- **Anomaly detection:** flags high/low temperature, heavy rainfall, strong winds, and sudden daily temperature changes.
- **Database:** stores each analyzed daily summary and anomaly result in a local file-backed H2 database.
- **API key safety:** reads `OPENWEATHER_API_KEY` from the environment; no real key is committed.

## OOP concepts

- **Encapsulation:** `WeatherRecord`, `Anomaly`, and `WeatherObservation` keep their data in objects.
- **Abstraction:** the forecast reader, analysis service, repository, and controller each have a specific job.
- **Composition:** each detection result combines weather data with any anomalies found.
- **Dependency injection:** Spring provides the repository to the service and the service to the controller.
- **Persistence:** `WeatherObservationRepository` uses Spring Data JPA to save and retrieve entity objects.

## Requirements

- JDK 17 or newer.
- Internet access on first build so the Maven Wrapper can download Maven and project dependencies.
- Internet access and an OpenWeatherMap API key for live analysis.

## Run on Windows PowerShell

Open PowerShell in the project folder. Set your key in that terminal session (replace the example text with your own key):

```powershell
$env:OPENWEATHER_API_KEY = "paste-your-api-key-here"
```

Build and start the app with the included Maven Wrapper (Maven does not need to be installed separately):

```powershell
.\mvnw.cmd spring-boot:run
```

On macOS or Linux, use `./mvnw spring-boot:run`. You can also open the project in IntelliJ IDEA, let it import `pom.xml`, then run `WeatherAnomalyDetectionSystem`. Keep the environment variable set in the IDE's run configuration.

## API endpoints

### Analyze a city

```text
GET http://localhost:8080/api/weather/analyze?city=London,GB
```

Fetches a new forecast, applies detection rules, stores each daily summary in H2, and returns the saved rows as JSON.

### View saved analyses

```text
GET http://localhost:8080/api/weather/history?city=London,GB
```

Returns analyses already stored for the city without calling the weather provider. The H2 database files are created in `data/` and excluded from Git.

You can open either URL in a browser while the app is running, or call it with PowerShell:

```powershell
Invoke-RestMethod "http://localhost:8080/api/weather/analyze?city=London,GB"
Invoke-RestMethod "http://localhost:8080/api/weather/history?city=London,GB"
```

## Detection rules

| Measurement | Example rule |
|---|---|
| Temperature | Daily average above 40°C or below 0°C |
| Rainfall | Daily total above 100 mm |
| Wind speed | Daily maximum above 80 km/h |
| Temperature change | Daily average changes by more than 10°C from the previous calendar day |

Change the thresholds in `WeatherAnalysisService.java` to fit the project demonstration. Forecast day grouping uses UTC. The first day may contain only a partial day because the forecast begins from the current time.

## Database

The project uses H2 in embedded file mode. The table is created and updated from the `WeatherObservation` entity. Local files are named `data/weatherdb.mv.db` and are ignored by Git, so each teammate gets their own local database.

## Project structure

```text
├── data/sample-weather.csv
├── pom.xml
├── src/main/java/weather/
│   ├── Anomaly.java
│   ├── AnomalyDetector.java
│   ├── CsvWeatherDataReader.java
│   ├── DetectionResult.java
│   ├── OpenWeatherMapForecastReader.java
│   ├── ReportPrinter.java
│   ├── WeatherAnalysisService.java
│   ├── WeatherAnomalyDetectionSystem.java
│   ├── WeatherController.java
│   ├── WeatherObservation.java
│   ├── WeatherObservationRepository.java
│   └── WeatherRecord.java
└── src/main/resources/application.properties
```

## Mini-project report outline

1. **Problem:** Manually reviewing many weather readings makes unusual values hard to notice.
2. **Objective:** Retrieve forecast data, identify readings outside selected limits, and persist the report.
3. **Method:** The REST controller calls the service; the service fetches data, runs rules, and saves results using a JPA repository backed by H2.
4. **Limitations:** Fixed thresholds can create false alarms, and forecast values are estimates rather than historical local climate baselines.
5. **Future improvements:** Add a web dashboard, user-configurable limits, historical climate comparisons, and a production database such as PostgreSQL.
