package basicOperation.util;

import lombok.Data;

@Data
public class SensorReading {
    private String id;
    private Long timestamp;
    private  Double temperature;

    public SensorReading() {
    }

    public SensorReading(String id, long timestamp, Double temperature) {
        this.id = id;
        this.timestamp = timestamp;
        this.temperature = temperature;
    }
}
