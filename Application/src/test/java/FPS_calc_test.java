import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;


enum FPS_BIN {
    VERY_LOW(0), LOW(1), NORMAL(2), HIGH(3), VERY_HIGH(4);
    private final int value;

    private FPS_BIN(int value) {
        this.value = value;
    }

    public static FPS_BIN get(int value_) {
        for (FPS_BIN bin: FPS_BIN.values()) {
            if(value_ == bin.value) {
                return bin;
            }
        }
        return null;
    }
}

enum FPSChange {
    INCREASE, DECREASE, NO_CHANGE;
}

class FPSDelta {
    int fpsChangeValue;

    FPSDelta(FPSChange direction, int changeBy) {
        if(direction == FPSChange.DECREASE && changeBy > 0) {
            fpsChangeValue = 0-changeBy;
        } else if(direction == FPSChange.DECREASE && changeBy == 0) {
            throw new IllegalArgumentException();
        } else if(direction == FPSChange.INCREASE && changeBy < 0) {
            fpsChangeValue = 0-changeBy;
        } else if(direction == FPSChange.INCREASE && changeBy == 0) {
            throw new IllegalArgumentException();
        } else if(direction == FPSChange.NO_CHANGE && changeBy != 0) {
            throw new IllegalArgumentException();
        }
    }
}

class FPSCalc {

    static FPSChange calc(int[] recentFPS) {
        int numRecordedFPSToUse = 5;
        int targetFPS = 5;
        if(numRecordedFPSToUse > recentFPS.length) {
            return FPSChange.NO_CHANGE;
        }
        int averageRecordedFPS = 0;
        averageRecordedFPS = average(recentFPS, numRecordedFPSToUse);
        if(averageRecordedFPS < targetFPS) {
            return FPSChange.INCREASE;
        } else if(averageRecordedFPS > targetFPS) {
            return FPSChange.DECREASE;
        }
        return FPSChange.NO_CHANGE;
    }

    private static int numRecordsToUse          = 5;
    private static int targetFPS                = 5;
    private static int targetConcurrentThreads  = 15;

    static FPSChange calc(int[] fps, int[] threadCounts) {
        boolean fpsCanIncrease = false;
        if(numRecordsToUse <= threadCounts.length) {
            int threadCountAverage = average(threadCounts, numRecordsToUse);
            if(threadCountAverage < targetConcurrentThreads) {
                fpsCanIncrease = true; }
            else if(threadCountAverage > targetConcurrentThreads) {
                return FPSChange.DECREASE; }
        }
        if(numRecordsToUse > fps.length) {
                return FPSChange.NO_CHANGE; }
        int fpsAverage = average(fps, numRecordsToUse);

        if(fpsAverage < targetFPS) {
            if(fpsCanIncrease) {
                return FPSChange.INCREASE; }
            else {
                return FPSChange.NO_CHANGE; }
        } else if(fpsAverage>targetFPS) {
                return FPSChange.DECREASE; }
        return FPSChange.NO_CHANGE;
    }



    public static FPS_BIN bin(final int normativeValue, int[] values, final int numValuesToUse) {
        final int bigger  = normativeValue + 2;
        final int smaller = normativeValue - 2;
        int[] bins = new int[5];
        for(int i_ = values.length - 1; i_ > (values.length - 1 - numValuesToUse); i_--) {
            if(values[i_] > bigger)                                         { bins[4]++; }
            else if(values[i_] <= bigger && values[i_] > normativeValue )   { bins[3]++; }
            else if(values[i_] == normativeValue )                          { bins[2]++; }
            else if(values[i_] < normativeValue && values[i_] >= smaller )  { bins[1]++; }
            else if(values[i_] < smaller )                                  { bins[0]++; }
        }
        int maxBin = 0, maxBinVal = 0;
        for(int i_ = 0; i_ < bins.length; i_++ ) {
            if(bins[i_] >= maxBinVal) {
                maxBinVal = bins[i_];
                maxBin    = i_;
            }
        }
        return FPS_BIN.get(maxBin);
    }

    public static FPS_BIN[] binTwice(final int normativeValue, int[] values, final int numValuesToUse, final int numValuesToUse2) {
        return new FPS_BIN[]{ bin(normativeValue, values, numValuesToUse), bin(normativeValue, values, numValuesToUse2)};
    }

    public static int average(int[] values, int numValuesToUse) {
        int averageValue, totalValue = 0;
        for(int i_ = values.length - 1; i_ > (values.length - 1 - numValuesToUse); i_--){
            totalValue = totalValue+values[i_];
        }
        averageValue = totalValue/numValuesToUse;
        if(totalValue%numValuesToUse > numValuesToUse/2) {
            averageValue = averageValue + 1;
        }
        return averageValue;
    }
}

public class FPS_calc_test {
    @Test
    public void testBinning() {
        assertThat(FPSCalc.bin(10, new int[]{100,100,10,1,1},  5), is(FPS_BIN.VERY_HIGH));
        assertThat(FPSCalc.bin(10, new int[]{100,10,10,1,100}, 5), is(FPS_BIN.VERY_HIGH));
        assertThat(FPSCalc.bin(10, new int[]{10,10,10,10,10},  5), is(FPS_BIN.NORMAL));
        assertThat(FPSCalc.bin(10, new int[]{10,10,10,10,10,10,10,10,10,10,20,20},  12), is(FPS_BIN.NORMAL));
        assertThat(FPSCalc.binTwice(10, new int[]{10,10,10,10,10,10,10,10,10,12,12,12},  12,  5), is( new FPS_BIN[]{FPS_BIN.NORMAL,FPS_BIN.HIGH}));
        assertThat(FPSCalc.binTwice(10, new int[]{10,10,10,10,10,10,10,10,10,8,8,8},  12,  5), is( new FPS_BIN[]{FPS_BIN.NORMAL,FPS_BIN.LOW}));
    }

    @Test
    public void calculateFPSChangeFromRecordedFPS() {
        assertThat(FPSCalc.calc(new int[]{}) , is(FPSChange.NO_CHANGE));
        assertThat(FPSCalc.calc(new int[]{1}) , is(FPSChange.NO_CHANGE));
        assertThat(FPSCalc.calc(new int[]{1,1000000}) , is(FPSChange.NO_CHANGE));
        assertThat(FPSCalc.calc(new int[]{1,1,1,1000000}) , is(FPSChange.NO_CHANGE));
        assertThat(FPSCalc.calc(new int[]{1,1,1,1,1}) , is(FPSChange.INCREASE));
        assertThat(FPSCalc.calc(new int[]{1,1,1,1,1,1,1,1}) , is(FPSChange.INCREASE));
//        assertThat(FPSCalc.calc(new int[]{1,1,1,1,1,1,1,10000000}) , is(FPSChange.INCREASE));
        assertThat(FPSCalc.calc(new int[]{10,10,10,10,10}) , is(FPSChange.DECREASE));
        assertThat(FPSCalc.calc(new int[]{10,10,10,10,10,10,10,10}) , is(FPSChange.DECREASE));
    }
    @Test
    public void calculateFPSChangeFromRecordedConcurrentThreads() {
        assertThat(FPSCalc.average(new int[]{15,15,15,15,15},5), is(15));
        assertThat(FPSCalc.average(new int[]{15,15,15,15,16},5), is(15));
        assertThat(FPSCalc.average(new int[]{15,15,15,16,16},5), is(15));
        assertThat(FPSCalc.average(new int[]{15,15,16,16,16},5), is(16));
        assertThat(FPSCalc.average(new int[]{16,15,16,15,16},5), is(16));
        assertThat(FPSCalc.calc(new int[]{}                         , new int[]{}) , is(FPSChange.NO_CHANGE));
        assertThat(FPSCalc.calc(new int[]{}                         , new int[]{25}) , is(FPSChange.NO_CHANGE));
        assertThat(FPSCalc.calc(new int[]{}                         , new int[]{25,25,25,25,25}) , is(FPSChange.DECREASE));
        assertThat(FPSCalc.calc(new int[]{1}                        , new int[]{}) , is(FPSChange.NO_CHANGE));
        assertThat(FPSCalc.calc(new int[]{1,1000000}                , new int[]{}) , is(FPSChange.NO_CHANGE));
        assertThat(FPSCalc.calc(new int[]{1,1,1,1000000}            , new int[]{}) , is(FPSChange.NO_CHANGE));
        assertThat(FPSCalc.calc(new int[]{1,1,1,1,1}                , new int[]{}) , is(FPSChange.NO_CHANGE));
        assertThat(FPSCalc.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{1}) , is(FPSChange.NO_CHANGE));
        assertThat(FPSCalc.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{1,1}) , is(FPSChange.NO_CHANGE));
        assertThat(FPSCalc.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{1,1,1}) , is(FPSChange.NO_CHANGE));
        assertThat(FPSCalc.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{1,1,1,1}) , is(FPSChange.NO_CHANGE));
        assertThat(FPSCalc.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{1,1,1,1,1}) , is(FPSChange.INCREASE));
        assertThat(FPSCalc.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{14,14,14,14,14}) , is(FPSChange.INCREASE));
        assertThat(FPSCalc.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{15,15,15,15,15}) , is(FPSChange.NO_CHANGE));
        assertThat(FPSCalc.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{15,15,15,15,16}) , is(FPSChange.NO_CHANGE));
        assertThat(FPSCalc.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{16,15,16,15,16}) , is(FPSChange.DECREASE));  // enough evidence to decrease
        assertThat(FPSCalc.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{15,16,16,15,15}) , is(FPSChange.NO_CHANGE));  // ? enough evidence to decrease ?
        assertThat(FPSCalc.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{15,16,16,15,14}) , is(FPSChange.NO_CHANGE));  // going down - not enough evidence to decrease
        assertThat(FPSCalc.calc(new int[]{1,1,1,1,1,1,1,1}          , new int[]{15,16,15,14,16}) , is(FPSChange.NO_CHANGE));  // fluctuating - too much variance to decrease
//        assertThat(FPSCalc.calc(new int[]{1,1,1,1,1,1,1,10000000}   , new int[]{}) , is(FPSChange.INCREASE));
        assertThat(FPSCalc.calc(new int[]{10,10,10,10,10}           , new int[]{}) , is(FPSChange.DECREASE));
        assertThat(FPSCalc.calc(new int[]{10,10,10,10,10,10,10,10}  , new int[]{}) , is(FPSChange.DECREASE));
    }
    @Test
    public void calculateFPSChangeFromRecordedConcurrentThreads2() {
        assertThat(FPSCalc.calc(new int[]{10,10,10,10,10}           , new int[]{}) , is(FPSChange.DECREASE));
        assertThat(FPSCalc.calc(new int[]{10,10,10,10}           , new int[]{}) , is(FPSChange.NO_CHANGE));
    }
}
