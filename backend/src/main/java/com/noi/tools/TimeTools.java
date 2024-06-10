package com.noi.tools;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.TimeZone;

public class TimeTools {
    public static final String DEFAULT_DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String DATETIME_PATTERN = "yyyyMMddHHmm";
    public static final String DAY_PATTERN = "yyyyMMdd";
    public static final TimeZone DEFAULT_TIMEZONE = TimeZone.getTimeZone("UTC");
    public static final TimeZone NYC_TIMEZONE = TimeZone.getTimeZone("America/New_York");

    public static Instant parseFromISO8601(String timeString) {
        if (timeString == null || timeString.isEmpty()) {
            return null;
        }
        // hack for weird time formats from RECUR
        if (timeString.length() > 19) {
            timeString = timeString.substring(0, 19) + ".000Z";
        }
        return Instant.parse(timeString);
    }

    public static Instant parseToInstant(String timeString) {
        if (timeString == null || timeString.isEmpty()) {
            return null;
        }
        return parseToInstant(timeString, DEFAULT_DATETIME_PATTERN, DEFAULT_TIMEZONE);
    }

    public static Instant parseToInstant(String timeString, TimeZone timeZone) {
        return parseToInstant(timeString, DEFAULT_DATETIME_PATTERN, timeZone);
    }

    public static Instant parseToInstant(String timeString, String pattern) {
        return parseToInstant(timeString, pattern, DEFAULT_TIMEZONE);
    }

    public static Instant parseToInstant(String timeString, String pattern, TimeZone timeZone) {
        if (timeString == null || timeString.isEmpty()) {
            return null;
        }
        timeString = timeString.replace("+00:00", "");
        //todo: do we need the timeZone here?
        DateTimeFormatter format = DateTimeFormatter.ofPattern(pattern, Locale.US).withZone(timeZone.toZoneId());
        LocalDateTime ldt = LocalDateTime.parse(timeString, format);
        return ldt.toInstant(ZoneOffset.UTC);
    }

    public static LocalDate parseDay(String timeString, String pattern) {
        if (timeString == null || timeString.isEmpty()) {
            return null;
        }
        DateTimeFormatter format = DateTimeFormatter.ofPattern(pattern, Locale.US);
        return LocalDate.parse(timeString, format);
    }

    public static void main(String[] args) {
        try {
            String dropDay = "20230530";
            System.out.println(TimeTools.addDay(dropDay, TimeTools.DAY_PATTERN, 365));

            System.out.println(parseToInstant("2023-07-10 20:00:00"));
            System.out.println(parseFromISO8601("2023-07-10T20:00:00.000Z"));
            String time = "2023-01-05T19:34:08.1796+00:00";
            System.out.println(parseFromISO8601(time));

            Instant now = Instant.now();
            System.out.println(convertToDateTimeStringUTC(now));
            System.out.println("tada!");

            System.out.println("is afternoon in NYC? " + isAfterNoon(NYC_TIMEZONE));

            System.out.println("signupCutoff:" + getSignupCutoffTimeUTC(Instant.now()));

            System.out.println("yesterday in NYC" + TimeTools.convertToDateStringNYC(Instant.now().minus(1, ChronoUnit.DAYS)));
            System.out.println("yesterday with time in UTC" + TimeTools.convertToDateTimeStringUTC(Instant.now().minus(1, ChronoUnit.DAYS)));


        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static String convertToDateTimeStringUTC(Instant instant) {
        return convertToDateTimeStringUTC(instant, DATETIME_PATTERN);
    }

    public static String convertToDateTimeStringUTC(Instant instant, String pattern) {
        DateTimeFormatter df = DateTimeFormatter.ofPattern(pattern)
                .withZone(DEFAULT_TIMEZONE.toZoneId());
        return df.format(instant);
    }

    /**
     * Get yyyyMMdd for time in UTC time zone
     *
     * @param time
     * @return
     */
    public static String convertToDateStringUTC(Instant time) {
        return convertToDateString(time, DEFAULT_TIMEZONE);
    }

    /**
     * Get yyyyMMdd for time in NYC time zone
     *
     * @param time
     * @return
     */
    public static String convertToDateStringNYC(Instant time) {
        return convertToDateString(time, NYC_TIMEZONE);
    }

    public static String convertToDateString(Instant time, TimeZone timeZone) {
        return convertToDateString(time, timeZone, DAY_PATTERN);
    }

    public static String convertToDateString(Instant time, TimeZone timeZone, String pattern) {
        DateTimeFormatter df = DateTimeFormatter.ofPattern(pattern)
                .withZone(timeZone.toZoneId());
        return df.format(time);
    }

    /**
     * convert a zoned date time string to utc:
     * 2023-10-30T05:42:37-12:00 => 202310301742
     * @param zonedDatetime
     * @return
     */
    public static String convertZonedToDateTimeStringUTC(String zonedDatetime) {
        OffsetDateTime offsetTime = OffsetDateTime.parse(zonedDatetime);
        ZonedDateTime utcTime = offsetTime.atZoneSameInstant(ZoneOffset.UTC);
        return TimeTools.convertToDateTimeStringUTC(utcTime.toInstant());
    }

    /**
     * is the time past noon in the provided timezone?
     *
     * @param timeZone
     * @return
     */
    public static boolean isAfterNoon(TimeZone timeZone) {
        LocalDateTime nycNow = LocalDateTime.now(ZoneId.of(timeZone.getID()));
        return nycNow.getHour() > 12;
    }

    public static boolean isAfter(Instant instant) {
        if (instant == null) {
            return false;
        }
        return Instant.now().isAfter(instant);
    }

    /**
     * get the date and time for the most recently allowed user signup (we cut at midnight of the day before now ... )
     *
     * @param time current time
     * @return date and time in format 'YYYYMMddHHmm' for midnight NYC, converted to UTC time zone (time strings are always in UTC!)
     */
    public static String getSignupCutoffTimeUTC(Instant time) {
        // YYYYMMddHHmm
        // get the day from now, minus 1, set hour to 23, and minute to 59
        DateTimeFormatter dayFormat = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US)
                .withZone(NYC_TIMEZONE.toZoneId());
        String yesterday = dayFormat.format(time.minus(1, ChronoUnit.DAYS));

        // midnight of the previous day, in NYC timezone
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm", Locale.US);
        Instant cutoff = LocalDateTime.parse(yesterday + "2359", formatter)
                .atZone(NYC_TIMEZONE.toZoneId())
                .toInstant();
        // convert to UTC
        return convertToDateTimeStringUTC(cutoff);
    }

    public static String addDay(String day, String dayPattern, int num) {
        LocalDate localDay = parseDay(day, dayPattern);
        localDay = localDay.plus(num, ChronoUnit.DAYS);

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(dayPattern);
        return localDay.format(dateTimeFormatter);
    }
}
