package basicOperation;

import basicOperation.util.SensorReading;
import basicOperation.util.SensorSource;
import basicOperation.util.SensorTimeAssigner;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;

public class KeyedTransformations {
    public static void main(String[] args) throws Exception {
        Configuration config = new Configuration();
        config.setInteger(RestOptions.PORT,8081);
        config.setString(RestOptions.BIND_ADDRESS,"localhost");
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(config);

        //use event time for the application
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        //configure watermark interval
        env.getConfig().setAutoWatermarkInterval(1000L);

        SingleOutputStreamOperator<SensorReading> readings = env.addSource(new SensorSource())
                //assign timestamps and watermarks which are required for event time
                .assignTimestampsAndWatermarks(new SensorTimeAssigner(Time.seconds(5L)));

        KeyedStream<SensorReading, String> KeyedStream = readings.keyBy(r -> r.getId());

        SingleOutputStreamOperator<SensorReading> maxTempPersensor = KeyedStream.reduce((r1, r2) -> r1.getTemperature() > r2.getTemperature() ? r1 : r2);

        maxTempPersensor.print();

        env.execute("Keyed Transformations Example");
    }
}
