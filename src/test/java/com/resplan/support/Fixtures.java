package com.resplan.support;

import com.resplan.booking.BookingCheckResult;
import com.resplan.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Фабрика объектов предметной области для модульных тестов. Сущности создаются без базы данных,
 * идентификаторы, которые обычно назначает СУБД, проставляются через отражение.
 */
public final class Fixtures {

    /** Понедельник: от этой даты удобно отсчитывать рабочие дни */
    public static final LocalDate MONDAY = LocalDate.parse("2026-10-05");
    /** Пятидневная неделя с праздником 7 ноября (производственный календарь Беларуси) */
    public static final WorkCalendar CALENDAR = new WorkCalendar("Беларусь",
            Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), Set.of(LocalDate.parse("2026-11-07")));
    public static final Location MINSK = new Location("Минск", CALENDAR);

    public static final Grade JUNIOR = withId(new Grade("Junior", 1), (short) 1);
    public static final Grade MIDDLE = withId(new Grade("Middle", 2), (short) 2);
    public static final Grade SENIOR = withId(new Grade("Senior", 3), (short) 3);
    public static final Grade LEAD = withId(new Grade("Lead", 4), (short) 4);

    public static final Skill JAVA = withId(new Skill("Java", false), 1);
    public static final Skill SPRING = withId(new Skill("Spring", false), 2);
    public static final Skill LLM = withId(new Skill("LLM", true), 3);

    private Fixtures() {
    }

    public static <T> T withId(T entity, Object id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    public static LocalDate day(int offset) {
        return MONDAY.plusDays(offset);
    }

    public static Employee employee(int id, String lastName, Grade grade, int hourlyRate) {
        return withId(new Employee(lastName, "Тест", grade, MINSK, BigDecimal.valueOf(hourlyRate)), id);
    }

    public static Employee employee(int id) {
        return employee(id, "Сотрудник" + id, MIDDLE, 20);
    }

    public static AppUser user(int id, UserRole role) {
        return withId(new AppUser(role.name().toLowerCase() + id, "hash", role, employee(100 + id)), id);
    }

    public static Project project(int id, String code, AppUser pm, int priority) {
        return withId(new Project(code, "Проект " + code, pm, MINSK, priority, MONDAY), id);
    }

    public static Task task(int id, Project project, String name, int duration, Task... predecessors) {
        return withId(new Task(project, name, duration, predecessors), id);
    }

    public static ProjectBooking projectBooking(long id, Employee e, Project p, BookingKind kind,
                                                LocalDate from, LocalDate to, int load) {
        return withId(new ProjectBooking(e, p, kind, from, to, load, null), id);
    }

    public static TrainingBooking training(long id, Employee e, LocalDate from, LocalDate to, int load,
                                           boolean protectedTime) {
        TrainingCourse course = withId(new TrainingCourse("Курс " + id, "EPAM University"), (int) id);
        return withId(new TrainingBooking(e, course, from, to, load, protectedTime, null), id);
    }

    public static ResourceRequest request(int id, Task task, Grade grade, int load, LocalDate from, LocalDate to) {
        return withId(new ResourceRequest(task, "Java-разработчик", grade, load, from, to, task.getProject().getPm()), id);
    }

    /** Результат проверки бронирования без нарушений с заданными перегрузками */
    public static BookingCheckResult passed(Booking... overloads) {
        BookingCheckResult result = new BookingCheckResult();
        result.getOverloads().addAll(List.of(overloads));
        return result;
    }
}
