package dev.salyvn.slBossSpawner.utils;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class TimeUtils {

    public static String formatDuration(long seconds) {
        Duration duration = Duration.ofSeconds(seconds);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long secs = duration.toSecondsPart();

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format("%d:%02d", minutes, secs);
    }

    public static String formatTime(ZonedDateTime time) {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    public static long getSecondsUntil(ZonedDateTime target) {
        return Duration.between(ZonedDateTime.now(target.getZone()), target).getSeconds();
    }

    public static ZonedDateTime getNextScheduledTime(String timeStr, ZoneId zone) {
        LocalTime localTime = LocalTime.parse(timeStr);
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime scheduled = now.with(localTime);

        if (scheduled.isBefore(now) || scheduled.isEqual(now)) {
            scheduled = scheduled.plusDays(1);
        }

        return scheduled;
    }
}
