package com.resplan.api.dto;

import com.resplan.service.ScheduleService.ProjectSchedule;

import java.time.LocalDate;
import java.util.List;

public record ScheduleDto(String project, LocalDate start, LocalDate finish, int durationDays,
                          List<String> criticalPath, List<Row> tasks) {

    public record Row(String name, int es, int ef, int ls, int lf, int slack, boolean critical,
                      LocalDate start, LocalDate end) {
    }

    public static ScheduleDto of(ProjectSchedule s) {
        List<Row> rows = s.tasks().stream().map(t -> {
            var c = t.schedule();
            return new Row(c.task().name(), c.es(), c.ef(), c.ls(), c.lf(), c.slack(), c.critical(), t.start(), t.end());
        }).toList();
        return new ScheduleDto(s.project().getCode(), s.project().getStartDate(), s.finish(), s.duration(),
                s.criticalPath(), rows);
    }
}
