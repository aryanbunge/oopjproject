package weather;

/** A readable description of an unusual measurement. */
public class Anomaly {
    private final String type;
    private final String explanation;

    public Anomaly(String type, String explanation) {
        this.type = type;
        this.explanation = explanation;
    }

    public String getType() { return type; }
    public String getExplanation() { return explanation; }
}
