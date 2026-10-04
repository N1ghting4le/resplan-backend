package com.resplan.service;

import com.resplan.domain.AppUser;
import com.resplan.domain.Project;
import com.resplan.domain.Task;
import com.resplan.domain.UserRole;
import com.resplan.error.BusinessRuleException;
import com.resplan.repository.ProjectRepository;
import com.resplan.repository.ResourceRequestRepository;
import com.resplan.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * UC-1: управление структурой задач проекта. После каждого изменения сетевой график
 * пересчитывается; цикл в зависимостях откатывает транзакцию (FR1-3, FR1-5).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProjectPlanService {

    private static final String EDIT_ACTION = "Изменять сетевой график";

    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final ResourceRequestRepository requests;
    private final ScheduleService schedules;

    @Transactional(readOnly = true)
    public List<Project> list(AppUser user) {
        return user.hasRole(UserRole.PM)
                ? projects.findByPmOrderByPriorityAscCodeAsc(user)
                : projects.findAllByOrderByPriorityAscCodeAsc();
    }

    @Transactional(readOnly = true)
    public Project get(int projectId) {
        return projects.require(projectId, "Проект");
    }

    @Transactional(readOnly = true)
    public List<Task> tasks(int projectId) {
        projects.require(projectId, "Проект");
        return tasks.findByProjectWithPredecessors(projectId);
    }

    public Task addTask(int projectId, TaskCommand cmd, AppUser pm) {
        Project project = projects.require(projectId, "Проект");
        project.requireManagedBy(pm, EDIT_ACTION);
        requireUniqueName(projectId, cmd.name(), null);
        Task task = new Task(project, cmd.name(), cmd.durationDays(),
                predecessors(projectId, cmd.predecessorIds()).toArray(Task[]::new));
        return tasks.save(task);
    }

    public Task updateTask(int projectId, int taskId, TaskCommand cmd, AppUser pm) {
        Task task = find(projectId, taskId);
        task.getProject().requireManagedBy(pm, EDIT_ACTION);
        requireUniqueName(projectId, cmd.name(), taskId);
        task.update(cmd.name(), cmd.durationDays(), predecessors(projectId, cmd.predecessorIds()));
        schedules.build(projectId); // пересчет графика; CycleException откатывает изменение
        return task;
    }

    public void deleteTask(int projectId, int taskId, AppUser pm) {
        Task task = find(projectId, taskId);
        task.getProject().requireManagedBy(pm, EDIT_ACTION);
        if (requests.existsByTask(task)) {
            throw new BusinessRuleException("TASK_IN_USE", "По задаче «" + task.getName()
                    + "» есть запросы на ресурсы; сначала отмените их");
        }
        tasks.findSuccessors(task).forEach(s -> s.removePredecessor(task));
        tasks.delete(task);
    }

    private Task find(int projectId, int taskId) {
        Task task = tasks.require(taskId, "Задача");
        if (!task.getProject().getId().equals(projectId)) {
            throw new BusinessRuleException("TASK_PROJECT", "Задача " + taskId + " не относится к проекту " + projectId);
        }
        return task;
    }

    /** Предшественники должны существовать и относиться к тому же проекту */
    private List<Task> predecessors(int projectId, List<Integer> ids) {
        if (ids == null) return List.of();
        return ids.stream().distinct().map(id -> {
            Task p = tasks.require(id, "Задача-предшественник");
            if (!p.getProject().getId().equals(projectId)) {
                throw new BusinessRuleException("DEPENDENCY", "Задача " + id + " относится к другому проекту");
            }
            return p;
        }).toList();
    }

    private void requireUniqueName(int projectId, String name, Integer exceptTaskId) {
        boolean duplicate = tasks.findByProjectWithPredecessors(projectId).stream()
                .anyMatch(t -> t.getName().equalsIgnoreCase(name.trim()) && !Objects.equals(t.getId(), exceptTaskId));
        if (duplicate) throw new BusinessRuleException("DUPLICATE", "В проекте уже есть задача «" + name + "»");
    }
}
