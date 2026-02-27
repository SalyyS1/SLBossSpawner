package dev.salyvn.slBossSpawner.statistics;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;

public enum PeriodType {
    DAILY, WEEKLY, MONTHLY, QUARTERLY, SEASONAL, YEARLY, ALL_TIME;

    // Periods that get archived (all except ALL_TIME)
    public static final PeriodType[] ARCHIVABLE = {DAILY, WEEKLY, MONTHLY, QUARTERLY, SEASONAL, YEARLY};

    public String computeKey(LocalDate date, SeasonResolver resolver) {
        return switch (this) {
            case DAILY -> date.toString();
            case WEEKLY -> {
                int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
                int year = date.get(WeekFields.ISO.weekBasedYear());
                yield year + "-W" + String.format("%02d", week);
            }
            case MONTHLY -> YearMonth.from(date).toString();
            case QUARTERLY -> {
                int q = (date.getMonthValue() - 1) / 3 + 1;
                yield date.getYear() + "-Q" + q;
            }
            case SEASONAL -> resolver.resolve(date);
            case YEARLY -> String.valueOf(date.getYear());
            case ALL_TIME -> "all";
        };
    }
}
