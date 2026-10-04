package com.resplan.service;

import java.util.List;

/** Параметры задачи сетевого графика: название, длительность в рабочих днях, предшественники (FS) */
public record TaskCommand(String name, int durationDays, List<Integer> predecessorIds) {
}
