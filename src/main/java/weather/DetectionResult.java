package weather;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Keeps a weather record together with the anomalies found for it. */
public class DetectionResult {
    private final WeatherRecord record;
    private final List<Anomaly> anomalies = new ArrayList<>();

    public DetectionResult(WeatherRecord record) { this.record = record; }
    public WeatherRecord getRecord() { return record; }
    public void addAnomaly(Anomaly anomaly) { anomalies.add(anomaly); }
    public List<Anomaly> getAnomalies() { return Collections.unmodifiableList(anomalies); }
    public boolean hasAnomalies() { return !anomalies.isEmpty(); }
}
