package com.resplan.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static com.resplan.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Производственный календарь, периоды и профиль загрузки")
class CalendarAndLoadTest {

    @Nested
    @DisplayName("WorkCalendar (правило BR-03)")
    class Calendar {

        @ParameterizedTest(name = "{0} -> рабочий день: {1}")
        @CsvSource({"2026-10-05, true", "2026-10-09, true", "2026-10-10, false", "2026-10-11, false",
                "2026-11-07, false", "2026-11-06, true"})
        @DisplayName("FR1-3: выходные и праздники не являются рабочими днями")
        void workdays(LocalDate date, boolean workday) {
            assertThat(CALENDAR.isWorkday(date)).isEqualTo(workday);
        }

        @ParameterizedTest(name = "рабочий день №{1} от {0} -> {2}")
        @CsvSource({"2026-10-05, 0, 2026-10-05", "2026-10-05, 4, 2026-10-09", "2026-10-05, 5, 2026-10-12",
                "2026-10-10, 0, 2026-10-12", "2026-11-02, 4, 2026-11-06", "2026-11-02, 10, 2026-11-16"})
        @DisplayName("FR1-3: перевод номера рабочего дня в календарную дату пропускает выходные")
        void workdayToDate(LocalDate start, int n, LocalDate expected) {
            assertThat(CALENDAR.workdayToDate(start, n)).isEqualTo(expected);
        }

        @Test
        @DisplayName("FR1-3: календарь с выходными в пятницу и субботу (Эр-Рияд)")
        void middleEastWeekend() {
            WorkCalendar riyadh = new WorkCalendar("Саудовская Аравия",
                    Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), Set.of(LocalDate.parse("2026-09-23")));

            assertThat(riyadh.isWorkday(LocalDate.parse("2026-10-11"))).isTrue();
            assertThat(riyadh.isWorkday(LocalDate.parse("2026-09-23"))).as("праздник в среду").isFalse();
            assertThat(riyadh.workdayToDate(LocalDate.parse("2026-09-22"), 1)).isEqualTo("2026-09-24");
            assertThat(riyadh.workdayToDate(LocalDate.parse("2026-10-08"), 1)).isEqualTo("2026-10-11");
        }
    }

    @Nested
    @DisplayName("CalendarPeriod")
    class Periods {

        @ParameterizedTest(name = "{0} для {1}: {2} – {3}")
        @CsvSource({
                "WEEK, 2026-10-07, 2026-10-05, 2026-10-11",
                "WEEK, 2026-10-05, 2026-10-05, 2026-10-11",
                "WEEK, 2026-11-01, 2026-10-26, 2026-11-01",
                "MONTH, 2026-10-15, 2026-10-01, 2026-10-31",
                "MONTH, 2028-02-10, 2028-02-01, 2028-02-29",
                "QUARTER, 2026-11-20, 2026-10-01, 2026-12-31",
                "QUARTER, 2026-02-01, 2026-01-01, 2026-03-31"})
        @DisplayName("FR8-1: границы недели, месяца и квартала, содержащих дату")
        void periodBounds(CalendarPeriod period, LocalDate date, LocalDate from, LocalDate to) {
            assertThat(period.start(date)).isEqualTo(from);
            assertThat(period.end(date)).isEqualTo(to);
        }
    }

    @Nested
    @DisplayName("LoadProfile")
    class Load {

        private final Employee kovalev = employee(1);
        private final Project atlas = project(1, "ATLAS", user(1, UserRole.PM), 1);

        @Test
        @DisplayName("FR8-2: дневная загрузка суммируется по броням только в рабочие дни")
        void loadIsSummedByWorkdays() {
            List<Booking> bookings = List.of(
                    projectBooking(1, kovalev, atlas, BookingKind.HARD, day(0), day(4), 100),
                    projectBooking(2, kovalev, atlas, BookingKind.HARD, day(3), day(9), 50),
                    training(3, kovalev, day(9), day(9), 50, true));

            LoadProfile profile = LoadProfile.of(CALENDAR, bookings, day(0), day(13));

            assertThat(profile.workdays()).isEqualTo(10);
            assertThat(profile.peak()).isEqualTo(150);
            // первая неделя 100+100+100+150+150, вторая 50+50+100+0+0: 800 за 10 рабочих дней
            assertThat(profile.average()).isCloseTo(80.0, within(1e-9));
        }

        @Test
        @DisplayName("FR8-3: период без броней – нулевая загрузка; выходные дни – нет рабочих дней")
        void emptyAndWeekendPeriods() {
            LoadProfile empty = LoadProfile.of(CALENDAR, List.of(), day(0), day(4));
            LoadProfile weekend = LoadProfile.of(CALENDAR, List.of(), day(5), day(6));

            assertThat(empty.workdays()).isEqualTo(5);
            assertThat(empty.average()).isZero();
            assertThat(empty.peak()).isZero();
            assertThat(weekend.workdays()).isZero();
            assertThat(weekend.average()).isZero();
        }
    }
}
