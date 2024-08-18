package basicOperation;

import basicOperation.util.SensorReading;
import basicOperation.util.SensorSource;
import basicOperation.util.SensorTimeAssigner;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;

public class basicTransformations {
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

        //filter out sensor measurements from sensors with temperature under 25 degrees
        SingleOutputStreamOperator<SensorReading> filteredSensors = readings.filter(r -> r.getTemperature() >= 25);


        //project the id of each sensor reading
        SingleOutputStreamOperator<String> sensorsIds = filteredSensors.map(r -> r.getId());


        //
        SingleOutputStreamOperator<String> splitIds = sensorsIds.flatMap(new FlatMapFunction<String, String>() {
            @Override
            public void flatMap(String s, Collector<String> collector) throws Exception {
                String[] s1 = s.split("_");
                for (String s2 : s1) {
                    collector.collect(s2);
                }
            }
        });

        splitIds.print();

        env.execute("Basic Transformations Example");
    }
}
