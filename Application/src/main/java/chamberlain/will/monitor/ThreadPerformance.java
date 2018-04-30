package chamberlain.will.monitor;

import chamberlain.will.datetime.DateTime;

/**
 * Created by will on 27/04/18.
 */

public class ThreadPerformance {

    private static DateTime dateTime = new DateTime();

    public DateTime.TimeAndThreadTimeMillis tic() {
        return dateTime.currentTimeAndThreadTimeMillis();
    }

    public DateTime.TimeAndThreadTimeMillis toc(DateTime.TimeAndThreadTimeMillis tic_) {
        return dateTime.diff(tic_);
    }

    public String tocLogString(DateTime.TimeAndThreadTimeMillis tic_) {
        DateTime.TimeAndThreadTimeMillis timeDiffMillis = toc(tic_);
        return "time elapsed=" + timeDiffMillis.timeMillis + "ms : thread time elapsed=" + timeDiffMillis.threadTimeMillis + "ms";
    }
}
