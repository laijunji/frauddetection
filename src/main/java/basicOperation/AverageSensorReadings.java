package basicOperation;

import basicOperation.util.SensorReading;
import basicOperation.util.SensorSource;
import basicOperation.util.SensorTimeAssigner;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

public class AverageSensorReadings {
    public static void main(String[] args) throws Exception {
        Configuration config = new Configuration();
        config.setInteger(RestOptions.PORT,8081);
        config.setString(RestOptions.BIND_ADDRESS,"localhost");
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(config);

        // use event time in application
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        
        //configure watermark interval
        env.getConfig().setAutoWatermarkInterval(1000L);
        
        //ingest sensor stream
        SingleOutputStreamOperator<SensorReading> sensorData = env
                .addSource(new SensorSource())
                .assignTimestampsAndWatermarks(new SensorTimeAssigner(Time.seconds(5L)));

        SingleOutputStreamOperator<SensorReading> avgTemp = sensorData.map(r -> (new SensorReading(r.getId(), r.getTimestamp(), (r.getTemperature() - 32) * (5.0 / 9.0))))
                .keyBy(r -> r.getId())
                .timeWindow(Time.seconds(1L))
                .apply(new TemperatureAverager());

        avgTemp.print();

        env.execute("Compute average sensor temperature");


    }
}

class TemperatureAverager implements WindowFunction<SensorReading, SensorReading, String, TimeWindow> {

    @Override
    public void apply(String s, TimeWindow timeWindow, Iterable<SensorReading> iterable, Collector<SensorReading> collector) throws Exception {
        int cnt = 0;
        double sum = 0;
        for (SensorReading sensorReading : iterable) {
            cnt++;
            sum = sum + sensorReading.getTemperature();
        }

        double avgTemp = sum / cnt;

        collector.collect(new SensorReading(s,timeWindow.getEnd(),avgTemp));
    }
}
