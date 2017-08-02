package com.example.android.camera2basic;



//class FPSDelta {
//    int fpsChangeValue;
//
//    FPSDelta(Change direction, int changeBy) {
//        if(direction == Change.DECREASE && changeBy > 0) {
//            fpsChangeValue = 0-changeBy;
//        } else if(direction == Change.DECREASE && changeBy == 0) {
//            throw new IllegalArgumentException();
//        } else if(direction == Change.INCREASE && changeBy < 0) {
//            fpsChangeValue = 0-changeBy;
//        } else if(direction == Change.INCREASE && changeBy == 0) {
//            throw new IllegalArgumentException();
//        } else if(direction == Change.NO_CHANGE && changeBy != 0) {
//            throw new IllegalArgumentException();
//        }
//    }
//}


public class FPS {
    public enum FPS_BIN {
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

    public enum Change {
        INCREASE, DECREASE, NO_CHANGE;
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

    public static Change calc(int[] recentFPS) {
        if(numRecordsToUse > recentFPS.length) {
            return Change.NO_CHANGE;
        }
        int averageRecordedFPS = 0;
        averageRecordedFPS = FPS.average(recentFPS, numRecordsToUse);
        if(averageRecordedFPS < targetFPS) {
            return Change.INCREASE;
        } else if(averageRecordedFPS > targetFPS) {
            return Change.DECREASE;
        }
        return Change.NO_CHANGE;
    }

    private static int numRecordsToUse          = 5;
    private static int targetFPS                = 5;
    private static int targetConcurrentThreads  = 15;

    public static Change calc(int[] fps, int[] threadCounts) {
        return calc(fps, threadCounts, numRecordsToUse, targetFPS, targetConcurrentThreads);
    }

    public static Change calc(int[] fps, int[] threadCounts, int numRecordsToUse_, int targetFPS_, int targetConcurrentThreads_) {
        boolean fpsCanIncrease = false;
        if(numRecordsToUse_ <= threadCounts.length) {
            int threadCountAverage = FPS.average(threadCounts, numRecordsToUse_);
            if(threadCountAverage < targetConcurrentThreads_) {
                fpsCanIncrease = true; }
            else if(threadCountAverage > targetConcurrentThreads_) {
                return Change.DECREASE; }
        }
        if(numRecordsToUse_ > fps.length) {
            return Change.NO_CHANGE; }
        int fpsAverage = FPS.average(fps, numRecordsToUse_);

        if(fpsAverage < targetFPS_) {
            if(fpsCanIncrease) {
                return Change.INCREASE; }
            else {
                return Change.NO_CHANGE; }
        } else if(fpsAverage>targetFPS_) {
            return Change.DECREASE; }
        return Change.NO_CHANGE;
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


}
