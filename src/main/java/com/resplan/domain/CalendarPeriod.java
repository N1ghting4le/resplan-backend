package com.resplan.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;

/** Период отображения персонального графика (FR8-1): неделя, месяц или квартал, содержащие дату */
public enum CalendarPeriod {
    WEEK, MONTH, QUARTER;

    public LocalDate start(LocalDate date) {
        return switch (this) {
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
            case QUARTER -> date.with(IsoFields.DAY_OF_QUARTER, 1);
        };
    }

    public LocalDate end(LocalDate date) {
        return switch (this) {
            case WEEK -> start(date).plusDays(6);
            case MONTH -> date.with(TemporalAdjusters.lastDayOfMonth());
            case QUARTER -> start(date).plusMonths(3).minusDays(1);
        };
    }
}
