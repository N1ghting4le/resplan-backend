package com.resplan.scheduling;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Метод критического пути (UC-1). Топологическая сортировка (алгоритм Кана)
 * с контролем циклов, прямой проход – ранние сроки, обратный – поздние.
 */
@Component
public class CriticalPathCalculator {

    public CpmResult calculate(List<TaskNode> nodes) {
        Map<Integer, TaskNode> byId = new HashMap<>();
        Map<Integer, List<Integer>> successors = new HashMap<>();
        Map<Integer, Integer> inDegree = new HashMap<>();
        for (TaskNode n : nodes) {
            byId.put(n.id(), n);
            successors.put(n.id(), new ArrayList<>());
        }
        for (TaskNode n : nodes) {
            List<Integer> preds = n.predecessors().stream().filter(byId::containsKey).toList();
            inDegree.put(n.id(), preds.size());
            preds.forEach(p -> successors.get(p).add(n.id()));
        }

        // Топологическая сортировка
        Deque<Integer> queue = new ArrayDeque<>();
        nodes.stream().filter(n -> inDegree.get(n.id()) == 0).forEach(n -> queue.add(n.id()));
        List<Integer> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            int id = queue.poll();
            order.add(id);
            for (int s : successors.get(id)) {
                if (inDegree.merge(s, -1, Integer::sum) == 0) queue.add(s);
            }
        }
        if (order.size() != nodes.size()) throw new CycleException();

        // Прямой проход: ранние сроки
        Map<Integer, Integer> es = new HashMap<>();
        Map<Integer, Integer> ef = new HashMap<>();
        int duration = 0;
        for (int id : order) {
            TaskNode t = byId.get(id);
            int start = t.predecessors().stream().filter(byId::containsKey).mapToInt(ef::get).max().orElse(0);
            es.put(id, start);
            ef.put(id, start + t.duration());
            duration = Math.max(duration, start + t.duration());
        }

        // Обратный проход: поздние сроки
        Map<Integer, Integer> ls = new HashMap<>();
        Map<Integer, Integer> lf = new HashMap<>();
        for (int i = order.size() - 1; i >= 0; i--) {
            int id = order.get(i);
            int finish = successors.get(id).stream().mapToInt(ls::get).min().orElse(duration);
            lf.put(id, finish);
            ls.put(id, finish - byId.get(id).duration());
        }

        List<ScheduledTask> tasks = nodes.stream()
                .map(n -> new ScheduledTask(n, es.get(n.id()), ef.get(n.id()), ls.get(n.id()), lf.get(n.id())))
                .toList();
        List<Integer> criticalPath = order.stream().filter(id -> ls.get(id).equals(es.get(id))).toList();
        return new CpmResult(tasks, duration, criticalPath);
    }
}
