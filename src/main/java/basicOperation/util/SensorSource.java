package basicOperation.util;


import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class SensorSource extends RichParallelSourceFunction<SensorReading> {

    // flag indicating whether source is still running
    private boolean running = true;

    /** run() continuously emits SensorReadings by emitting them through the SourceContext. */
    @Override
    public void run(SourceContext<SensorReading> srcCtx) throws Exception {

        // initialize random number generator
        Random rand = new Random();
        // look up index of this parallel task
        int taskIdx = this.getRuntimeContext().getIndexOfThisSubtask();

        // initialize sensor ids and temperatures
        String[] sensorIds = new String[10];
        double[] curFTemp = new double[10];
        for (int i = 0; i < 10; i++) {
            sensorIds[i] = "sensor_" + (taskIdx * 10 + i);
            curFTemp[i] = 65 + (rand.nextGaussian() * 20);
        }

        while (running) {

            // get current time
            long curTime = Calendar.getInstance().getTimeInMillis();

            // emit SensorReadings
            for (int i = 0; i < 10; i++) {
                // update current temperature
                curFTemp[i] += rand.nextGaussian() * 0.5;
                // emit reading
                srcCtx.collect(new SensorReading(sensorIds[i], curTime, curFTemp[i]));
            }

            // wait for 100 ms
            Thread.sleep(100);
        }
    }

    /** Cancels this SourceFunction. */
    @Override
    public void cancel() {
        this.running = false;
    }
}