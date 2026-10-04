package com.resplan.service;

import com.resplan.domain.AppUser;
import com.resplan.domain.Project;
import com.resplan.domain.Task;
import com.resplan.domain.UserRole;
import com.resplan.repository.ProjectRepository;
import com.resplan.repository.TaskRepository;
import com.resplan.scheduling.CriticalPathCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static com.resplan.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UC-1 Расчет сетевого графика с датами: ScheduleService")
class ScheduleServiceTest {

    @Mock
    ProjectRepository projects;
    @Mock
    TaskRepository tasks;
    @Spy
    CriticalPathCalculator calculator = new CriticalPathCalculator();
    @InjectMocks
    ScheduleService service;

    @Test
    @DisplayName("FR1-3: рабочие дни переводятся в даты с пропуском выходных и праздников")
    void scheduleUsesProjectCalendar() {
        AppUser pm = user(1, UserRole.PM);
        // проект стартует в понедельник 26.10.2026; 07.11 – праздник (суббота)
        Project p = withId(new Project("TEST", "Тестовый проект", pm, MINSK, 1, LocalDate.parse("2026-10-26")), 1);
        Task a = task(1, p, "Анализ", 5);
        Task b = task(2, p, "Разработка", 7, a);
        Task c = task(3, p, "Документация", 2, a);
        when(projects.require(1, "Проект")).thenReturn(p);
        when(tasks.findByProjectWithPredecessors(1)).thenReturn(List.of(a, b, c));

        ScheduleService.ProjectSchedule schedule = service.build(1);

        assertThat(schedule.duration()).isEqualTo(12);
        assertThat(schedule.criticalPath()).containsExactly("Анализ", "Разработка");
        assertThat(schedule.finish()).isEqualTo("2026-11-10");
        ScheduleService.TaskDates analysis = schedule.tasks().get(0);
        assertThat(analysis.start()).isEqualTo("2026-10-26");
        assertThat(analysis.end()).isEqualTo("2026-10-30");
        ScheduleService.TaskDates development = schedule.tasks().get(1);
        assertThat(development.start()).isEqualTo("2026-11-02");
        assertThat(development.end()).isEqualTo("2026-11-10");
        assertThat(schedule.tasks().get(2).schedule().slack()).isEqualTo(5);
    }

    @Test
    @DisplayName("FR1-3: задача нулевой длительности (веха) начинается и заканчивается в один день")
    void milestoneHasSingleDate() {
        Project p = project(1, "ATLAS", user(1, UserRole.PM), 1);
        Task start = task(1, p, "Старт", 0);
        when(projects.require(1, "Проект")).thenReturn(p);
        when(tasks.findByProjectWithPredecessors(1)).thenReturn(List.of(start));

        ScheduleService.ProjectSchedule schedule = service.build(1);

        assertThat(schedule.duration()).isZero();
        assertThat(schedule.finish()).isEqualTo(MONDAY);
        assertThat(schedule.tasks().get(0).start()).isEqualTo(schedule.tasks().get(0).end());
    }
}
