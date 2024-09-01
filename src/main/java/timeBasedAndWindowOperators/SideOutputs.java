package timeBasedAndWindowOperators;

import basicOperation.util.SensorReading;
import basicOperation.util.SensorSource;
import basicOperation.util.SensorTimeAssigner;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

public class SideOutputs {
    public static void main(String[] args) throws Exception {
        OutputTag<String> freezingAlarmOutput = new OutputTag<String>("freezing-alarms"){};
        Configuration config = new Configuration();
        config.setInteger(RestOptions.PORT,8081);
        config.setString(RestOptions.BIND_ADDRESS,"localhost");
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(config);


        //checkpoint
        env.getCheckpointConfig().setCheckpointInterval(10 * 1000);

        //use event time for the application
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        //watermark
        env.getConfig().setAutoWatermarkInterval(1000L);

        SingleOutputStreamOperator<SensorReading> readings = env.addSource(new SensorSource())
                .assignTimestampsAndWatermarks(new SensorTimeAssigner(Time.seconds(10)));

        //split stream
        SingleOutputStreamOperator<SensorReading> monitoredReadings = readings.process(new FreezingMonitor(freezingAlarmOutput));

        //retrieve the freezing alarms
        monitoredReadings.getSideOutput(freezingAlarmOutput)
                .print();

        //print main stream
        readings.print();

        env.execute();

    }
}

class FreezingMonitor extends ProcessFunction<SensorReading,SensorReading>{

    private OutputTag<String> freezingAlarmOutput;

    public FreezingMonitor(OutputTag<String> freezingAlarmOutput) {
        this.freezingAlarmOutput = freezingAlarmOutput;
    }


    @Override
    public void processElement(SensorReading sensorReading, ProcessFunction<SensorReading, SensorReading>.Context ctx, Collector<SensorReading> out) throws Exception {
        if(sensorReading.getTemperature() <32.0){
            ctx.output(this.freezingAlarmOutput,"Freezing Alarm for" + sensorReading.getId());
        }

        //main stream
        out.collect(sensorReading);
    }
}