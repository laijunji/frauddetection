package basicOperation;

import basicOperation.util.*;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.CoFlatMapFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;

public class MultiStreamTransformatins {
    public static void main(String[] args) throws Exception {
        Configuration config = new Configuration();
        config.setInteger(RestOptions.PORT,8081);
        config.setString(RestOptions.BIND_ADDRESS,"localhost");
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(config);

        //use event time for the application
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        //configure watermark interval
        env.getConfig().setAutoWatermarkInterval(1000L);

        //ingest sensor stream
        SingleOutputStreamOperator<SensorReading> tempReadings = env.addSource(new SensorSource()).assignTimestampsAndWatermarks(new SensorTimeAssigner(Time.seconds(5L)));

        //ingest smoke level stream
        DataStreamSource<SmokeLevel> smokeReadings = env.addSource(new SmokeLevelSource()).setParallelism(1);

        KeyedStream<SensorReading, String> keyed = tempReadings.keyBy(r -> r.getId());

        SingleOutputStreamOperator<Alert> alerts = keyed.connect(smokeReadings.broadcast())
                .flatMap(new CoFlatMapFunction<SensorReading, SmokeLevel, Alert>() {
                    private SmokeLevel smokeLevel = SmokeLevel.LOW;

                    @Override
                    public void flatMap1(SensorReading sensorReading, Collector<Alert> collector) throws Exception {
                        if (smokeLevel.equals(SmokeLevel.HIGH) && sensorReading.getTemperature() > 100) {
                            collector.collect(new Alert("Risk of fire!", sensorReading.getTimestamp()));
                        }
                    }

                    @Override
                    public void flatMap2(SmokeLevel smokeLevel, Collector<Alert> collector) throws Exception {
                        this.smokeLevel = smokeLevel;
                    }
                });

        alerts.print();

        env.execute("Multi-stream Transformations Example");
    }
}
