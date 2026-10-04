package com.resplan.service;

import java.time.LocalDate;
import java.util.Map;

/** Параметры нового запроса на ресурс; skills – навык и минимальный уровень */
public record NewRequestCommand(int taskId, String role, String grade, int loadPercent,
                                LocalDate startDate, LocalDate endDate, Map<String, Integer> skills) {
}
