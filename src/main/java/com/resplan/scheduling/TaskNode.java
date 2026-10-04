package com.resplan.scheduling;

import java.util.List;

/** Вершина сетевого графика: задача, длительность в рабочих днях и предшественники (FS) */
public record TaskNode(int id, String name, int duration, List<Integer> predecessors) {
}
