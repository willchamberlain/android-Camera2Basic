package chamberlain.will.datetime;

import android.os.SystemClock;

/**
 * Facade over the available date and time functions/libraries, to save looking things up all the time.
 * Maybe convert to Strategy pattern if the flexibility is ever needed.
 *
 * Created by will on 27/04/18.
 */

public class DateTime {
    /**
     * Returns the current time in milliseconds.  Note that
     * while the unit of time of the return value is a millisecond,
     * the granularity of the value depends on the underlying
     * operating system and may be larger.  For example, many
     * operating systems measure time in units of tens of
     * milliseconds.
     */
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /** Returns milliseconds running in the current thread. */
    public long currentThreadTimeMillis() {
        return SystemClock.currentThreadTimeMillis();
    }


    /** Returns microseconds running in the current thread.
     * @return elapsed microseconds in the thread     */
    public long currentThreadTimeMicro() {
        // return SystemClock.currentThreadTimeMicro();
        return currentThreadTimeMillis()*1000;
    }

    /** Returns current wall time in  microseconds.
     * @return elapsed microseconds in wall time     */
    public long currentTimeMicro() {
        return currentTimeMillis()*1000;
    }


    //---------------------------------------------------------------

    public TimeAndThreadTimeMillis currentTimeAndThreadTimeMillis() {
        return new TimeAndThreadTimeMillis(currentTimeMillis(), currentThreadTimeMillis());
    }

    public TimeAndThreadTimeMillis diff(TimeAndThreadTimeMillis start_) {
        return new TimeAndThreadTimeMillis(
                currentTimeMillis() - start_.timeMillis ,
                currentThreadTimeMillis() - start_.threadTimeMillis );
    }

    public TimeAndThreadTimeMillis diff(TimeAndThreadTimeMillis start_, TimeAndThreadTimeMillis end_) {
        return new TimeAndThreadTimeMillis(
                end_.timeMillis - start_.timeMillis ,
                end_.threadTimeMillis - start_.threadTimeMillis );
    }


    //---------------------------------------------------------------


    public class TimeAndThreadTimeMillis {
        public long timeMillis;
        public long threadTimeMillis;

        public TimeAndThreadTimeMillis(long timeMillis, long threadTimeMillis) {
            this.timeMillis = timeMillis;
            this.threadTimeMillis = threadTimeMillis;
        }
    }

}