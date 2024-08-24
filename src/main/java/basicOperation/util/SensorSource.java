package basicOperation.util;


import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;


public class SensorSource extends RichParallelSourceFunction<SensorReading> {

    public boolean running = true;


    @Override
    public void run(SourceContext<SensorReading> sourceContext) throws Exception {
        //initialize random number generator
        Random rand = new Random();

        //look up index of this parallel task
        int taskIdx = this.getRuntimeContext().getTaskInfo().getIndexOfThisSubtask();

        ArrayList<Tuple2<String,Double>> curFTemp = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            curFTemp.add(new Tuple2<>("sensor_" + (taskIdx * 10 + i), 65 + (rand.nextGaussian() * 20)));
        }

        while(running){
            ArrayList<Tuple2<String, Double>> cur = curFTemp.stream()
                    .map(i -> new Tuple2<>(i.f0, i.f1 + rand.nextGaussian() * 0.5))
                    .collect(Collectors.toCollection(ArrayList::new));

            long curTime = Calendar.getInstance().getTimeInMillis();

            //emit new SensorReading
            for (int i = 0; i < curFTemp.size(); i++) {
                sourceContext.collect(new SensorReading(cur.get(i).f0,curTime,cur.get(i).f1));
            }

            Thread.sleep(100L);
        }
    }

    @Override
    public void cancel() {
        this.running = false;
    }
}
