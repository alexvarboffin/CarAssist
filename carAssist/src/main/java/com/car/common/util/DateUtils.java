
package com.car.common.util;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class DateUtils {

    public static long getUnixStamp() {
        return System.currentTimeMillis() / 1000;
    }
    
    public static long getTodayBeginTimestamp() {
        Date date = new Date();
        GregorianCalendar gc = new GregorianCalendar();
        gc.setTime(date);
        Date date2 = new Date(date.getTime() - gc.get(Calendar.HOUR_OF_DAY) * 60 * 60
                * 1000 - gc.get(Calendar.MINUTE) * 60 * 1000 - gc.get(Calendar.SECOND)
                * 1000);
        return date2.getTime()/1000;
    }
    
    public static long getYesterdayBeginTimestamp() {
        return getTodayBeginTimestamp() - 24*60*60;
    }

}
