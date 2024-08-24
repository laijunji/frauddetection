package basicOperation;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.util.Arrays;

public class RollingSum {
    public static void main(String[] args) throws Exception {
        Configuration config = new Configuration();
        config.setInteger(RestOptions.PORT,8081);
        config.setString(RestOptions.BIND_ADDRESS,"localhost");
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(config);

        //use event time for the application
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        //configure watermark interval
        env.getConfig().setAutoWatermarkInterval(1000L);

        DataStreamSource<int[]> inputStream = env.fromElements(new int[][]{{1, 2, 2}, {2, 3, 1}, {2, 2, 4}, {1, 5, 3}, {1, 2, 2}, {2, 3, 1}, {2, 2, 4}, {1, 5, 3}});

        SingleOutputStreamOperator<int[]> resultStream = inputStream.keyBy(i -> i[0]).sum(1);

        resultStream.map(new MapFunction<int[], String>() {
            @Override
            public String map(int[] ints) throws Exception {
                return Arrays.toString(ints);
            }
        }).print();

        env.execute("Rolling Sum Example");
    }
}
