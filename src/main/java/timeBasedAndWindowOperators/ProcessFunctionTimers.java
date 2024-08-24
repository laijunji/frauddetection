package timeBasedAndWindowOperators;

import basicOperation.util.SensorReading;
import basicOperation.util.SensorSource;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

public class ProcessFunctionTimers {
    public static void main(String[] args) throws Exception {
        Configuration config = new Configuration();
        config.setInteger(RestOptions.PORT,8081);
        config.setString(RestOptions.BIND_ADDRESS,"localhost");
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(config);

        //use event time for the application
        env.setStreamTimeCharacteristic(TimeCharacteristic.ProcessingTime);

        env.setParallelism(1);

        DataStreamSource<SensorReading> readings = env.addSource(new SensorSource());

        SingleOutputStreamOperator<String> warnings = readings.keyBy(SensorReading::getId).process(new TempIncreaseAlertFunction());

        warnings.print();

        env.execute("Monitor sensor temperatures.");
    }
}


class TempIncreaseAlertFunction extends KeyedProcessFunction<String, SensorReading, String>{

    private ValueState<Double> lastTemp;

    private ValueState<Long> currentTimer;

    @Override
    public void onTimer(long timestamp, KeyedProcessFunction<String, SensorReading, String>.OnTimerContext ctx, Collector<String> out) throws Exception {

            out.collect("Temperature of sensor '" + ctx.getCurrentKey() +
                    "' monotonically increased for 0.5 second.");
            currentTimer.clear();

    }


    @Override
    public void processElement(SensorReading sensorReading, KeyedProcessFunction<String, SensorReading, String>.Context context, Collector<String> collector) throws Exception {
        Double prevTemp = this.lastTemp.value();

        this.lastTemp.update(sensorReading.getTemperature());

        Long curTimerTimeStamp = this.currentTimer.value();


        if (prevTemp == null) {
//            collector.collect("branch_1");
        } else if(sensorReading.getTemperature() < prevTemp && curTimerTimeStamp != null) {
//            collector.collect("branch_2" + context.getCurrentKey() + sensorReading + "prevTemp:" + prevTemp);
            context.timerService().deleteProcessingTimeTimer(curTimerTimeStamp);
            currentTimer.clear();
        }else if(sensorReading.getTemperature() >= prevTemp && curTimerTimeStamp == null){
//            collector.collect("branch_3" + context.getCurrentKey() + sensorReading + "prevTemp:" + prevTemp);
            long timerTs = context.timerService().currentProcessingTime() + 500L;
            context.timerService().registerProcessingTimeTimer(timerTs);
            currentTimer.update(timerTs);
        }else {
//            collector.collect("branch_4" + context.getCurrentKey() + sensorReading + "prevTemp:" + prevTemp);
        }

    }

    @Override
    public void open(OpenContext openContext) throws Exception {

        this.lastTemp = getRuntimeContext().getState(new ValueStateDescriptor<Double>("lastTemp",Double.class));

        this.currentTimer = getRuntimeContext().getState(new ValueStateDescriptor<Long>("timer",Long.class));

    }

}
