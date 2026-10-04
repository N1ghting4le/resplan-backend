package com.resplan.api;

import com.resplan.api.dto.ProjectDto;
import com.resplan.api.dto.ScheduleDto;
import com.resplan.api.dto.TaskDto;
import com.resplan.api.dto.TaskRequest;
import com.resplan.domain.AppUser;
import com.resplan.service.ProjectPlanService;
import com.resplan.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Tag(name = "UC-1 Управлять расписанием и сетевым графиком")
public class ProjectController {

    private final ProjectPlanService plans;
    private final ScheduleService schedules;

    @GetMapping
    @Operation(summary = "Список проектов (для PM – только его проекты)")
    public List<ProjectDto> list(@CurrentUser AppUser user) {
        return plans.list(user).stream().map(ProjectDto::of).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Карточка проекта")
    public ProjectDto get(@PathVariable int id) {
        return ProjectDto.of(plans.get(id));
    }

    @GetMapping("/{id}/tasks")
    @Operation(summary = "Задачи проекта и связи между ними")
    public List<TaskDto> tasks(@PathVariable int id) {
        return plans.tasks(id).stream().map(TaskDto::of).toList();
    }

    @PostMapping("/{id}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PM')")
    @Operation(summary = "Добавить задачу в сетевой график")
    public TaskDto addTask(@CurrentUser AppUser pm, @PathVariable int id, @Valid @RequestBody TaskRequest dto) {
        return TaskDto.of(plans.addTask(id, dto.toCommand(), pm));
    }

    @PutMapping("/{id}/tasks/{taskId}")
    @PreAuthorize("hasRole('PM')")
    @Operation(summary = "Изменить задачу: название, длительность, предшественников")
    public TaskDto updateTask(@CurrentUser AppUser pm, @PathVariable int id, @PathVariable int taskId,
                              @Valid @RequestBody TaskRequest dto) {
        return TaskDto.of(plans.updateTask(id, taskId, dto.toCommand(), pm));
    }

    @DeleteMapping("/{id}/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PM')")
    @Operation(summary = "Удалить задачу из сетевого графика")
    public void deleteTask(@CurrentUser AppUser pm, @PathVariable int id, @PathVariable int taskId) {
        plans.deleteTask(id, taskId, pm);
    }

    @GetMapping("/{id}/schedule")
    @Operation(summary = "Сетевой график: ранние и поздние сроки, резервы, критический путь")
    public ScheduleDto schedule(@PathVariable int id) {
        return ScheduleDto.of(schedules.build(id));
    }
}
