
package com.almende.dialog.util;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;


public class TimeUtils
{
    private static final String serverTimezone = "Europe/Amsterdam";

    public static DateTimeZone getServerDateTimeZone()
    {
        return DateTimeZone.forID( serverTimezone );
    }
    
    public static TimeZone getServerTimeZone()
    {
        return TimeZone.getTimeZone( serverTimezone );
    }

    public static DateTime getServerCurrentTime()
    {
        return DateTime.now( getServerDateTimeZone() );
    }

    public static long getServerCurrentTimeInMillis()
    {
        return getServerCurrentTime().getMillis();
    }

    /**
     * returns true if the timestampInMillis is not more than timeWindowInMillis from the current timestamp  
     * @param timestampInMillis
     * @param timeWindowInMillis
     * @return
     */
    public static boolean isCurrentTimeWithinWindow( long timestampInMillis, long timeWindowInMillis )
    {
        if ( timestampInMillis == 0L )
        {
            return true;
        }
        else
        {
            return getServerCurrentTimeInMillis() - timestampInMillis <= timeWindowInMillis;
        }
    }

    /**
     * returns the current server milliseconds + the minutes supplied 
     * @param minutes
     * @return
     */
    public static long getCurrentServerTimePlusMinutes(int minutes)
    {
        return getServerCurrentTime().plusMinutes( minutes ).getMillis();
    }

    /**
     * formats the supplied time in millis to the one specifiedin the format.
     * @param pDateTime
     * @param format E.g. "dd-MM-yyyy HH:mm:ss Z"
     * @return
     */
    public static String getStringFormatFromDateTime( long pDateTime, String format )
    {
        SimpleDateFormat dateFormat = new SimpleDateFormat();
        dateFormat.setTimeZone(getServerTimeZone());
        return dateFormat.format( new Date( pDateTime ) );
    }

    /**
     * e.g. 10:45:42 in HH:mm:ss or January 02, 2010 in "MMMM d, yyyy" etc to
     * its DateTime equivalent format
     * 
     * @param time
     * @param dateFormat
     * @return
     * @throws ASKFastCheckedException
     */
    public static DateTime getTimeWithFormat( String stringDate, String dateFormat, DateTimeZone timeZone, Locale locale )
    {
        locale = locale != null ? locale : Locale.ENGLISH;
        timeZone = timeZone != null ? timeZone : getServerDateTimeZone();
        try
        {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat( dateFormat, locale );
            simpleDateFormat.setTimeZone( TimeZone.getTimeZone( timeZone.getID() ) );
            Date parsedDate = simpleDateFormat.parse( stringDate );
            return new DateTime( parsedDate, timeZone );
        }
        catch ( ParseException e )
        {
            return null;
        }
    }
}
