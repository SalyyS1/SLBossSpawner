package dev.salyvn.slBossSpawner.statistics;

import java.time.LocalDate;

// Maps calendar quarters to S1-S4: Jan-Mar=S1, Apr-Jun=S2, Jul-Sep=S3, Oct-Dec=S4
public class CalendarQuarterSeasonResolver implements SeasonResolver {
    @Override
    public String resolve(LocalDate date) {
        int season = (date.getMonthValue() - 1) / 3 + 1;
        return date.getYear() + "-S" + season;
    }
}
