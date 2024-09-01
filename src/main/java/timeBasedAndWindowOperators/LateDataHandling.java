package timeBasedAndWindowOperators;

import basicOperation.util.SensorReading;
import basicOperation.util.SensorSource;
import basicOperation.util.SensorTimeAssigner;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.util.Random;

public class LateDataHandling {
    private static OutputTag<SensorReading> lateReadingsOutput= new OutputTag<SensorReading>("side-output"){};

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


        //solution1 as follows:
        //SingleOutputStreamOperator<SensorReading> mainStream = outOfOrderreadings.process(new filterLateReadings());
        //mainStream.getSideOutput(lateReadingsOutput).print();
        //mainStream.print();

        //solution2 as follows:
        //sideOutputLateEventsWindow(outOfOrderreadings);

        //solution3 as follows:
        updateForLateEventsWindow(outOfOrderreadings);

        env.execute("late data handling.");

    }

    public static class filterLateReadings extends  ProcessFunction<SensorReading, SensorReading>{

        @Override
        public void processElement(SensorReading sensorReading, ProcessFunction<SensorReading, SensorReading>.Context ctx, Collector<SensorReading> collector) throws Exception {
            if (sensorReading.getTimestamp() < ctx.timerService().currentWatermark()) {
                ctx.output(lateReadingsOutput, sensorReading);
            }
            collector.collect(sensorReading);
        }
    }

    public static void sideOutputLateEventsWindow(DataStream<SensorReading> readings){
        SingleOutputStreamOperator<Tuple3<String, Long, Long>> countPer10Secs = readings.keyBy(i -> i.getId())
                .timeWindow(Time.seconds(10L))
                //emit late readings to a side output
                .sideOutputLateData(lateReadingsOutput)
                .process(
                        new ProcessWindowFunction<SensorReading, Tuple3<String, Long, Long>, String, TimeWindow>() {
                            @Override
                            public void process(String s, ProcessWindowFunction<SensorReading, Tuple3<String, Long, Long>, String, TimeWindow>.Context ctx, Iterable<SensorReading> elements, Collector<Tuple3<String, Long, Long>> out) throws Exception {
                                Long cnt = 0L;
                                for (SensorReading e : elements) {
                                    cnt++;
                                }
                                out.collect(new Tuple3<String, Long, Long>(s, ctx.window().getEnd(), cnt));
                            }
                        }
                );
        countPer10Secs.getSideOutput(lateReadingsOutput)
                .map(r -> "*** late reading ***" + r.getId())
                .print();
        countPer10Secs.print();
    }


    public static void updateForLateEventsWindow(DataStream<SensorReading> readings){
        SingleOutputStreamOperator<Tuple4<String, Long, Long, String>> countPer10Secs = readings.keyBy(i -> i.getId())
                .timeWindow(Time.seconds(10))
                //process late readings for 5 additional seconds
                .allowedLateness(Time.seconds(5))
                .process(new UpdatingWindowCountFunction());

        countPer10Secs.print();

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


class UpdatingWindowCountFunction extends ProcessWindowFunction<SensorReading, Tuple4<String,Long,Long,String>,String, TimeWindow>{
    private ValueState<Boolean> isUpdate;

    @Override
    public void open(OpenContext openContext) throws Exception {
        ValueStateDescriptor<Boolean> descriptor =
                new ValueStateDescriptor<>(
                        "isUpdate", // the state name
                        TypeInformation.of(new TypeHint<Boolean>() {}), // type information
                        false); // default value of the state, if nothing was set
        isUpdate = getRuntimeContext().getState(descriptor);
    }

    @Override
    public void process(String s, ProcessWindowFunction<SensorReading, Tuple4<String, Long, Long, String>, String, TimeWindow>.Context ctx, Iterable<SensorReading> elements, Collector<Tuple4<String, Long, Long, String>> out) throws Exception {
        Long cnt = 0L;
        for (SensorReading e : elements) {
            cnt++;
        }

        if(!isUpdate.value()){
            out.collect(new Tuple4<>(s,ctx.window().getEnd(),cnt,"first"));
            isUpdate.update(true);
        }else{
            out.collect(new Tuple4<>(s,ctx.window().getEnd(), cnt,"update"));
        }
    }
}
