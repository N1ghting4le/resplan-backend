package com.resplan.scheduling;

import java.util.List;

public record CpmResult(List<ScheduledTask> tasks, int duration, List<Integer> criticalPath) {
}
