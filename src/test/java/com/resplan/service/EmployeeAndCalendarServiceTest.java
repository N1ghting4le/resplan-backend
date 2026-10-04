package com.resplan.service;

import com.resplan.domain.*;
import com.resplan.error.NotFoundException;
import com.resplan.repository.BookingRepository;
import com.resplan.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static com.resplan.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UC-7, UC-8 Пул сотрудников и персональный график: EmployeeService, CalendarService")
class EmployeeAndCalendarServiceTest {

    @Mock
    EmployeeRepository employees;
    @Mock
    BookingRepository bookings;

    Employee kovalev;
    Employee lis;
    Employee yusupova;
    Project atlas;

    @BeforeEach
    void setUp() {
        kovalev = employee(1, "Ковалёв", MIDDLE, 22).addSkill(JAVA, 4);
        lis = employee(8, "Лис", SENIOR, 24).addSkill(SPRING, 2);
        yusupova = employee(9, "Юсупова", JUNIOR, 12).addSkill(JAVA, 1);
        atlas = project(1, "ATLAS", user(1, UserRole.PM), 1);
    }

    @Nested
    @DisplayName("EmployeeService")
    class Pool {

        EmployeeService service;

        @BeforeEach
        void setUp() {
            service = new EmployeeService(employees, bookings);
        }

        @Test
        @DisplayName("FR7-1, FR7-3: загрузка сотрудника на дату – сумма действующих броней")
        void loadIsSumOfBookings() {
            List<Booking> busy = List.of(
                    projectBooking(1, kovalev, atlas, BookingKind.HARD, day(0), day(9), 100),
                    projectBooking(2, kovalev, atlas, BookingKind.HARD, day(0), day(30), 50),
                    projectBooking(3, lis, atlas, BookingKind.SOFT, day(0), day(9), 50));
            when(bookings.findOverlapping(day(2), day(2))).thenReturn(busy);
            when(employees.findActiveWithSkills()).thenReturn(List.of(kovalev, lis, yusupova));

            List<EmployeeService.EmployeeLoad> pool = service.list(day(2), null, false);

            assertThat(pool).extracting(EmployeeService.EmployeeLoad::loadPercent).containsExactly(150, 50, 0);
            assertThat(pool).extracting(EmployeeService.EmployeeLoad::bench).containsExactly(false, false, true);
        }

        @Test
        @DisplayName("FR7-4: фильтр Bench – только сотрудники без броней, фильтр по навыку без учета регистра")
        void benchAndSkillFilters() {
            when(bookings.findOverlapping(day(2), day(2))).thenReturn(
                    List.of(projectBooking(1, kovalev, atlas, BookingKind.HARD, day(0), day(9), 100)));
            when(employees.findActiveWithSkills()).thenReturn(List.of(kovalev, lis, yusupova));

            assertThat(service.list(day(2), null, true)).extracting(l -> l.employee().getLastName())
                    .containsExactly("Лис", "Юсупова");
            assertThat(service.list(day(2), "java", false)).extracting(l -> l.employee().getLastName())
                    .containsExactly("Ковалёв", "Юсупова");
            assertThat(service.list(day(2), "java", true)).extracting(l -> l.employee().getLastName())
                    .containsExactly("Юсупова");
        }

        @Test
        @DisplayName("FR7-1: профиль сотрудника с загрузкой на дату")
        void employeeProfile() {
            when(employees.require(1, "Сотрудник")).thenReturn(kovalev);
            when(bookings.findOverlapping(1, day(2), day(2))).thenReturn(List.of());

            EmployeeService.EmployeeLoad profile = service.get(1, day(2));

            assertThat(profile.employee()).isSameAs(kovalev);
            assertThat(profile.bench()).isTrue();
        }
    }

    @Nested
    @DisplayName("CalendarService")
    class Calendar {

        CalendarService service;

        @BeforeEach
        void setUp() {
            service = new CalendarService(employees, bookings);
        }

        @Test
        @DisplayName("FR8-1, FR8-2: календарь за месяц – проекты, обучение и средняя загрузка по рабочим дням")
        void monthCalendar() {
            List<Booking> items = List.of(
                    projectBooking(1, kovalev, atlas, BookingKind.HARD, LocalDate.parse("2026-10-14"), LocalDate.parse("2026-10-27"), 100),
                    training(2, kovalev, LocalDate.parse("2026-10-30"), LocalDate.parse("2026-10-30"), 50, true));
            when(employees.require(1, "Сотрудник")).thenReturn(kovalev);
            when(bookings.findOverlapping(1, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))).thenReturn(items);

            CalendarService.CalendarView view = service.view(1, CalendarPeriod.MONTH, LocalDate.parse("2026-10-20"));

            assertThat(view.from()).isEqualTo("2026-10-01");
            assertThat(view.to()).isEqualTo("2026-10-31");
            assertThat(view.bookings()).hasSize(2);
            assertThat(view.load().workdays()).isEqualTo(22);
            assertThat(view.load().peak()).isEqualTo(100);
            // 10 рабочих дней по 100 % и один день 50 %: 1050 / 22
            assertThat(view.load().average()).isEqualTo(1050.0 / 22);
            assertThat(view.bench()).isFalse();
        }

        @Test
        @DisplayName("FR8-3: календарь без броней – сотрудник в резерве (Bench)")
        void emptyCalendarIsBench() {
            when(employees.require(9, "Сотрудник")).thenReturn(yusupova);
            when(bookings.findOverlapping(9, day(0), day(6))).thenReturn(List.of());

            CalendarService.CalendarView view = service.view(9, CalendarPeriod.WEEK, day(3));

            assertThat(view.bench()).isTrue();
            assertThat(view.load().workdays()).isEqualTo(5);
            assertThat(view.load().average()).isZero();
        }

        @Test
        @DisplayName("FR8-1: календарь несуществующего сотрудника – ошибка «не найден»")
        void unknownEmployee() {
            when(employees.require(99, "Сотрудник")).thenThrow(new NotFoundException("Сотрудник", 99));

            assertThatThrownBy(() -> service.view(99, CalendarPeriod.QUARTER, day(0)))
                    .isInstanceOf(NotFoundException.class);
        }
    }
}
