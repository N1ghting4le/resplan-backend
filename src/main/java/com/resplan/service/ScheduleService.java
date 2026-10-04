package com.resplan.service;

import com.resplan.domain.Project;
import com.resplan.domain.Task;
import com.resplan.domain.WorkCalendar;
import com.resplan.repository.ProjectRepository;
import com.resplan.repository.TaskRepository;
import com.resplan.scheduling.CpmResult;
import com.resplan.scheduling.CriticalPathCalculator;
import com.resplan.scheduling.ScheduledTask;
import com.resplan.scheduling.TaskNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** UC-1: расчет сетевого графика проекта с переводом рабочих дней в календарные даты */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final CriticalPathCalculator calculator;

    public record TaskDates(ScheduledTask schedule, LocalDate start, LocalDate end) {
    }

    public record ProjectSchedule(Project project, List<TaskDates> tasks, int duration,
                                  LocalDate finish, List<String> criticalPath) {
    }

    public ProjectSchedule build(int projectId) {
        Project project = projects.require(projectId, "Проект");
        List<Task> list = tasks.findByProjectWithPredecessors(projectId);
        List<TaskNode> nodes = list.stream()
                .map(t -> new TaskNode(t.getId(), t.getName(), t.getDurationDays(),
                        t.getPredecessors().stream().map(Task::getId).toList()))
                .toList();
        CpmResult cpm = calculator.calculate(nodes);

        WorkCalendar calendar = project.getLocation().getCalendar();
        LocalDate base = project.getStartDate();
        List<TaskDates> dated = cpm.tasks().stream()
                .map(s -> new TaskDates(s, calendar.workdayToDate(base, s.es()),
                        calendar.workdayToDate(base, Math.max(s.es(), s.ef() - 1))))
                .toList();
        List<String> path = cpm.criticalPath().stream()
                .map(id -> nodes.stream().filter(n -> n.id() == id).findFirst().orElseThrow().name())
                .toList();
        LocalDate finish = calendar.workdayToDate(base, Math.max(0, cpm.duration() - 1));
        return new ProjectSchedule(project, dated, cpm.duration(), finish, path);
    }
}
