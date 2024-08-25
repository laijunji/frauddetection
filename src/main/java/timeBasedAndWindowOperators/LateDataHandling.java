package timeBasedAndWindowOperators;

import basicOperation.util.SensorReading;
import basicOperation.util.SensorSource;
import basicOperation.util.SensorTimeAssigner;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.util.Random;

public class LateDataHandling {
    private static OutputTag<? super String> lateReadingsOutput= new OutputTag<String>("side-output"){};

    public static void main(String[] args) throws Exception {
        Configuration config = new Configuration();
        config.setInteger(RestOptions.PORT,8081);
        config.setString(RestOptions.BIND_ADDRESS,"localhost");
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(config);
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        env.getCheckpointConfig().setCheckpointInterval(10 * 1000L);
        env.getConfig().setAutoWatermarkInterval(500L);
        SingleOutputStreamOperator<SensorReading> outOfOrderreadings = env.addSource(new SensorSource())
                .map(new TimestampShuffler(7 * 1000L))
                .assignTimestampsAndWatermarks(new SensorTimeAssigner(Time.seconds(5L)));

        SingleOutputStreamOperator<SensorReading> mainStream = outOfOrderreadings.process(new ProcessFunction<SensorReading, SensorReading>() {
            @Override
            public void processElement(SensorReading sensorReading, ProcessFunction<SensorReading, SensorReading>.Context ctx, Collector<SensorReading> collector) throws Exception {
                if (sensorReading.getTimestamp() < ctx.timerService().currentWatermark()) {
                    ctx.output(lateReadingsOutput, "sideout-" + sensorReading);
                }
                collector.collect(sensorReading);
            }
        });

        mainStream.getSideOutput(lateReadingsOutput).print();

        //mainStream.print();

        env.execute("late data handling.");

    }
}

class TimestampShuffler implements MapFunction<SensorReading, SensorReading> {
    private final Long maxRandomOffset;

    private Random rand = new Random();

    public TimestampShuffler(Long maxRandomOffset) {
        this.maxRandomOffset = maxRandomOffset;
    }

    @Override
    public SensorReading map(SensorReading sensorReading) throws Exception {
        Long shuffleTs = sensorReading.getTimestamp() + rand.nextInt(Math.toIntExact(this.maxRandomOffset));
        return new SensorReading(sensorReading.getId(),shuffleTs,sensorReading.getTemperature());
    }
}
