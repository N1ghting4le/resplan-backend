package com.resplan.service;

import com.resplan.domain.*;
import com.resplan.error.AccessDeniedException;
import com.resplan.error.BusinessRuleException;
import com.resplan.error.NotFoundException;
import com.resplan.repository.ProjectRepository;
import com.resplan.repository.ResourceRequestRepository;
import com.resplan.repository.TaskRepository;
import com.resplan.scheduling.CycleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.resplan.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UC-1 Управлять расписанием и сетевым графиком: ProjectPlanService")
class ProjectPlanServiceTest {

    @Mock
    ProjectRepository projects;
    @Mock
    TaskRepository tasks;
    @Mock
    ResourceRequestRepository requests;
    @Mock
    ScheduleService schedules;
    @InjectMocks
    ProjectPlanService service;

    AppUser pm;
    AppUser otherPm;
    Project atlas;
    Task analysis;
    Task design;

    @BeforeEach
    void setUp() {
        pm = user(1, UserRole.PM);
        otherPm = user(2, UserRole.PM);
        atlas = project(1, "ATLAS", pm, 1);
        analysis = task(1, atlas, "Анализ требований", 5);
        design = task(2, atlas, "Проектирование архитектуры", 4, analysis);
    }

    @Nested
    @DisplayName("FR1-1 Карточка проекта и сетевой график")
    class Read {

        @Test
        @DisplayName("FR1-1: PM видит только свои проекты, остальные роли – все проекты")
        void projectListDependsOnRole() {
            Project helix = project(3, "HELIX", otherPm, 2);
            when(projects.findByPmOrderByPriorityAscCodeAsc(pm)).thenReturn(List.of(atlas));
            when(projects.findAllByOrderByPriorityAscCodeAsc()).thenReturn(List.of(atlas, helix));

            assertThat(service.list(pm)).containsExactly(atlas);
            assertThat(service.list(user(3, UserRole.RM))).containsExactly(atlas, helix);
        }

        @Test
        @DisplayName("FR1-1: задачи проекта загружаются вместе с зависимостями")
        void tasksOfProject() {
            when(projects.require(1, "Проект")).thenReturn(atlas);
            when(tasks.findByProjectWithPredecessors(1)).thenReturn(List.of(analysis, design));

            assertThat(service.get(1)).isSameAs(atlas);
            assertThat(service.tasks(1)).containsExactly(analysis, design);
        }

        @Test
        @DisplayName("FR1-1: несуществующий проект – ошибка «не найден»")
        void unknownProject() {
            when(projects.require(42, "Проект")).thenThrow(new NotFoundException("Проект", 42));

            assertThatThrownBy(() -> service.tasks(42)).isInstanceOf(NotFoundException.class)
                    .hasMessage("Проект с идентификатором 42 не найден");
            verifyNoInteractions(tasks);
        }
    }

    @Nested
    @DisplayName("FR1-2, FR1-4 Добавление и изменение задач")
    class Edit {

        @Test
        @DisplayName("FR1-2, FR1-4: задача добавляется с длительностью и предшественниками и сохраняется")
        void addTaskWithDependencies() {
            when(projects.require(1, "Проект")).thenReturn(atlas);
            when(tasks.findByProjectWithPredecessors(1)).thenReturn(List.of(analysis, design));
            when(tasks.require(2, "Задача-предшественник")).thenReturn(design);
            when(tasks.save(any(Task.class))).then(returnsFirstArg());

            Task added = service.addTask(1, new TaskCommand("Backend API", 10, List.of(2, 2)), pm);

            assertThat(added.getName()).isEqualTo("Backend API");
            assertThat(added.getDurationDays()).isEqualTo((short) 10);
            assertThat(added.getPredecessors()).containsExactly(design);
            assertThat(added.getProject()).isSameAs(atlas);
            verify(tasks).save(added);
        }

        @Test
        @DisplayName("FR1-2: название задачи уникально в проекте без учета регистра")
        void duplicateNameIsRejected() {
            when(projects.require(1, "Проект")).thenReturn(atlas);
            when(tasks.findByProjectWithPredecessors(1)).thenReturn(List.of(analysis, design));

            assertThatThrownBy(() -> service.addTask(1, new TaskCommand(" анализ ТРЕБОВАНИЙ ", 3, null), pm))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("code", "DUPLICATE");
            verify(tasks, never()).save(any());
        }

        @Test
        @DisplayName("FR1-2: предшественник из другого проекта запрещен")
        void foreignPredecessorIsRejected() {
            Task foreign = task(11, project(2, "ORION", pm, 2), "Аудит инфраструктуры", 5);
            when(projects.require(1, "Проект")).thenReturn(atlas);
            when(tasks.findByProjectWithPredecessors(1)).thenReturn(List.of());
            when(tasks.require(11, "Задача-предшественник")).thenReturn(foreign);

            assertThatThrownBy(() -> service.addTask(1, new TaskCommand("Интеграция", 4, List.of(11)), pm))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("code", "DEPENDENCY");
        }

        @Test
        @DisplayName("FR1-4: изменять график может только PM проекта")
        void foreignPmCannotEdit() {
            when(projects.require(1, "Проект")).thenReturn(atlas);

            assertThatThrownBy(() -> service.addTask(1, new TaskCommand("Задача", 1, null), otherPm))
                    .isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(tasks);
        }

        @Test
        @DisplayName("FR1-3: после изменения задачи сетевой график пересчитывается")
        void updateRecalculatesSchedule() {
            when(tasks.require(2, "Задача")).thenReturn(design);
            when(tasks.findByProjectWithPredecessors(1)).thenReturn(List.of(analysis, design));

            Task updated = service.updateTask(1, 2, new TaskCommand("Проектирование архитектуры", 6, null), pm);

            assertThat(updated.getDurationDays()).isEqualTo((short) 6);
            assertThat(updated.getPredecessors()).isEmpty();
            verify(schedules).build(1);
        }

        @Test
        @DisplayName("FR1-5: цикл в зависимостях отклоняет изменение")
        void cycleRejectsUpdate() {
            when(tasks.require(1, "Задача")).thenReturn(analysis);
            when(tasks.require(2, "Задача-предшественник")).thenReturn(design);
            when(tasks.findByProjectWithPredecessors(1)).thenReturn(List.of(analysis, design));
            when(schedules.build(1)).thenThrow(new CycleException());

            assertThatThrownBy(() -> service.updateTask(1, 1, new TaskCommand("Анализ требований", 5, List.of(2)), pm))
                    .isInstanceOf(CycleException.class);
        }

        @Test
        @DisplayName("FR1-2: задача другого проекта по адресу проекта не изменяется")
        void taskOfOtherProject() {
            when(tasks.require(2, "Задача")).thenReturn(design);

            assertThatThrownBy(() -> service.updateTask(7, 2, new TaskCommand("X", 1, null), pm))
                    .hasFieldOrPropertyWithValue("code", "TASK_PROJECT");
        }
    }

    @Nested
    @DisplayName("Удаление задач")
    class Delete {

        @Test
        @DisplayName("FR1-2: при удалении задачи связи последователей снимаются")
        void deleteRemovesDependencies() {
            when(tasks.require(1, "Задача")).thenReturn(analysis);
            when(requests.existsByTask(analysis)).thenReturn(false);
            when(tasks.findSuccessors(analysis)).thenReturn(List.of(design));

            service.deleteTask(1, 1, pm);

            assertThat(design.getPredecessors()).isEmpty();
            verify(tasks).delete(analysis);
        }

        @Test
        @DisplayName("FR1-2: задачу с запросами на ресурсы удалить нельзя")
        void taskWithRequestsIsKept() {
            when(tasks.require(2, "Задача")).thenReturn(design);
            when(requests.existsByTask(design)).thenReturn(true);

            assertThatThrownBy(() -> service.deleteTask(1, 2, pm))
                    .hasFieldOrPropertyWithValue("code", "TASK_IN_USE");
            verify(tasks, never()).delete(any());
        }
    }
}
