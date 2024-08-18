package basicOperation.util;


import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor;
import org.apache.flink.streaming.api.windowing.time.Time;


public class SensorTimeAssigner extends BoundedOutOfOrdernessTimestampExtractor<SensorReading> {
    public SensorTimeAssigner(Time maxOutOfOrderness) {
        super(maxOutOfOrderness.toDuration());
    }

    @Override
    public long extractTimestamp(SensorReading sensorReading) {
        return sensorReading.getTimestamp();
    }
}
