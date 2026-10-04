package com.resplan.api.dto;

import com.resplan.domain.Task;

import java.util.List;

public record TaskDto(int id, String name, int durationDays, Integer assigneeId, String assignee,
                      List<Integer> predecessorIds) {

    public static TaskDto of(Task t) {
        var a = t.getAssignee();
        return new TaskDto(t.getId(), t.getName(), t.getDurationDays(),
                a == null ? null : a.getId(), a == null ? null : a.fullName(),
                t.getPredecessors().stream().map(Task::getId).sorted().toList());
    }
}
