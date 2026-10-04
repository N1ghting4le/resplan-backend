package com.resplan.service;

import java.time.LocalDate;

/** Параметры блока обучения (FR9-2); protectedTime – флаг «Запрет на проектное бронирование» */
public record TrainingCommand(int employeeId, String courseTitle, String provider,
                              LocalDate startDate, LocalDate endDate, int loadPercent, boolean protectedTime) {
}
