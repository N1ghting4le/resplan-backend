package com.resplan.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ежедневная загрузка сотрудника по рабочим дням периода с учетом производственного календаря (BR-03).
 * Используется и при подборе кандидатов (UC-5), и в персональном графике (UC-8) – расчет описан один раз.
 */
public final class LoadProfile {

    private final Map<LocalDate, Integer> daily = new LinkedHashMap<>();

    private LoadProfile() {
    }

    public static LoadProfile of(WorkCalendar calendar, Collection<? extends Booking> bookings,
                                 LocalDate from, LocalDate to) {
        LoadProfile profile = new LoadProfile();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (!calendar.isWorkday(d)) continue;
            int load = 0;
            for (Booking b : bookings) {
                if (b.overlaps(d, d)) load += b.getLoadPercent();
            }
            profile.daily.put(d, load);
        }
        return profile;
    }

    public int workdays() {
        return daily.size();
    }

    /** Средняя загрузка по рабочим дням, %; для периода без рабочих дней – 0 */
    public double average() {
        return daily.values().stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    /** Максимальная дневная загрузка, % */
    public int peak() {
        return daily.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }
}
