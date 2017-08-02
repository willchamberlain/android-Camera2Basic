import com.example.android.camera2basic.FPS;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;



public class FPS_calc_test {
    @Test
    public void testBinning() {
        assertThat(FPS.bin(10, new int[]{100,100,10,1,1},  5), is(FPS.FPS_BIN.VERY_HIGH));
        assertThat(FPS.bin(10, new int[]{100,10,10,1,100}, 5), is(FPS.FPS_BIN.VERY_HIGH));
        assertThat(FPS.bin(10, new int[]{10,10,10,10,10},  5), is(FPS.FPS_BIN.NORMAL));
        assertThat(FPS.bin(10, new int[]{10,10,10,10,10,10,10,10,10,10,20,20},  12), is(FPS.FPS_BIN.NORMAL));
        assertThat(FPS.binTwice(10, new int[]{10,10,10,10,10,10,10,10,10,12,12,12},  12,  5), is( new FPS.FPS_BIN[]{FPS.FPS_BIN.NORMAL, FPS.FPS_BIN.HIGH}));
        assertThat(FPS.binTwice(10, new int[]{10,10,10,10,10,10,10,10,10,8,8,8},  12,  5), is( new FPS.FPS_BIN[]{FPS.FPS_BIN.NORMAL, FPS.FPS_BIN.LOW}));
    }

    @Test
    public void calculateFPSChangeFromRecordedFPS() {
        assertThat(FPS.calc(new int[]{}) , is(FPS.Change.NO_CHANGE));
        assertThat(FPS.calc(new int[]{1}) , is(FPS.Change.NO_CHANGE));
        assertThat(FPS.calc(new int[]{1,1000000}) , is(FPS.Change.NO_CHANGE));
        assertThat(FPS.calc(new int[]{1,1,1,1000000}) , is(FPS.Change.NO_CHANGE));
        assertThat(FPS.calc(new int[]{1,1,1,1,1}) , is(FPS.Change.INCREASE));
        assertThat(FPS.calc(new int[]{1,1,1,1,1,1,1,1}) , is(FPS.Change.INCREASE));
//        assertThat(FPS.calc(new int[]{1,1,1,1,1,1,1,10000000}) , is(FPS.Change.INCREASE));
        assertThat(FPS.calc(new int[]{10,10,10,10,10}) , is(FPS.Change.DECREASE));
        assertThat(FPS.calc(new int[]{10,10,10,10,10,10,10,10}) , is(FPS.Change.DECREASE));
    }
    @Test
    public void calculateFPSChangeFromRecordedConcurrentThreads() {
        assertThat(FPS.average(new int[]{15,15,15,15,15},5), is(15));
        assertThat(FPS.average(new int[]{15,15,15,15,16},5), is(15));
        assertThat(FPS.average(new int[]{15,15,15,16,16},5), is(15));
        assertThat(FPS.average(new int[]{15,15,16,16,16},5), is(16));
        assertThat(FPS.average(new int[]{16,15,16,15,16},5), is(16));
        assertThat(FPS.calc(new int[]{}                         , new int[]{}) , is(FPS.Change.NO_CHANGE));
        assertThat(FPS.calc(new int[]{}                         , new int[]{25}) , is(FPS.Change.NO_CHANGE));
        assertThat(FPS.calc(new int[]{}                         , new int[]{25,25,25,25,25}) , is(FPS.Change.DECREASE));
        assertThat(FPS.calc(new int[]{1}                        , new int[]{}) , is(FPS.Change.NO_CHANGE));
        assertThat(FPS.calc(new int[]{1,1000000}                , new int[]{}) , is(FPS.Change.NO_CHANGE));
        assertThat(FPS.calc(new int[]{1,1,1,1000000}            , new int[]{}) , is(FPS.Change.NO_CHANGE));
        assertThat(FPS.calc(new int[]{1,1,1,1,1}                , new int[]{}) , is(FPS.Change.NO_CHANGE));
        assertThat(FPS.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{1}) , is(FPS.Change.NO_CHANGE));
        assertThat(FPS.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{1,1}) , is(FPS.Change.NO_CHANGE));
        assertThat(FPS.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{1,1,1}) , is(FPS.Change.NO_CHANGE));
        assertThat(FPS.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{1,1,1,1}) , is(FPS.Change.NO_CHANGE));
        assertThat(FPS.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{1,1,1,1,1}) , is(FPS.Change.INCREASE));
        assertThat(FPS.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{14,14,14,14,14}) , is(FPS.Change.INCREASE));
        assertThat(FPS.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{15,15,15,15,15}) , is(FPS.Change.NO_CHANGE));
        assertThat(FPS.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{15,15,15,15,16}) , is(FPS.Change.NO_CHANGE));
        assertThat(FPS.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{16,15,16,15,16}) , is(FPS.Change.DECREASE));  // enough evidence to decrease
        assertThat(FPS.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{15,16,16,15,15}) , is(FPS.Change.NO_CHANGE));  // ? enough evidence to decrease ?
        assertThat(FPS.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{15,16,16,15,14}) , is(FPS.Change.NO_CHANGE));  // going down - not enough evidence to decrease
        assertThat(FPS.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{15,16,15,14,16}) , is(FPS.Change.NO_CHANGE));  // fluctuating - too much variance to decrease
//        assertThat(FPS.calc(new int[]{1,1,1,1,1,1,1,10000000}   , new int[]{}) , is(FPS.Change.INCREASE));
        assertThat(FPS.calc(new int[]{10,10,10,10,10}           , new int[]{}) , is(FPS.Change.DECREASE));
        assertThat(FPS.calc(new int[]{10,10,10,10,10,10,10,10}  , new int[]{}) , is(FPS.Change.DECREASE));
    }
    @Test
    public void calculateFPSChangeFromRecordedConcurrentThreads2() {
        assertThat(FPS.calc(new int[]{10,10,10,10,10}           , new int[]{}) , is(FPS.Change.DECREASE));
        assertThat(FPS.calc(new int[]{10,10,10,10}           , new int[]{}) , is(FPS.Change.NO_CHANGE));
    }
}
