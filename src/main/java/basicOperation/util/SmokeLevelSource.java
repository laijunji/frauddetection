package basicOperation.util;

import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;


import java.util.Random;

/**
 * Flink SourceFunction to generate random SmokeLevel events
 */
public class SmokeLevelSource extends RichParallelSourceFunction<SmokeLevel> {

    private boolean running = true;

    @Override
    public void run(SourceContext<SmokeLevel> sourceContext) throws Exception {
        Random rand = new Random();

        while (running){
            if (rand.nextGaussian() > 0.8) {
                sourceContext.collect(SmokeLevel.HIGH);
            } else{
                sourceContext.collect(SmokeLevel.LOW);
            }
        }

        Thread.sleep(1000);
    }

    @Override
    public void cancel() {
        running = false;
    }
}
