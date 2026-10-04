package com.resplan.scheduling;

import com.resplan.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CriticalPathCalculatorTest {

    private final CriticalPathCalculator calculator = new CriticalPathCalculator();

    private static TaskNode node(int id, int duration, Integer... preds) {
        return new TaskNode(id, "t" + id, duration, List.of(preds));
    }

    @Test
    void atlasNetworkHasDuration34AndKnownCriticalPath() {
        List<TaskNode> atlas = List.of(
                node(1, 5), node(2, 4, 1), node(3, 6, 1), node(4, 10, 2), node(5, 12, 2, 3),
                node(6, 8, 2), node(7, 4, 4, 5, 6), node(8, 5, 7), node(9, 2, 8), node(10, 3, 6));

        CpmResult result = calculator.calculate(atlas);

        assertThat(result.duration()).isEqualTo(34);
        assertThat(result.criticalPath()).containsExactly(1, 3, 5, 7, 8, 9);
        ScheduledTask backend = result.tasks().get(3);
        assertThat(backend.es()).isEqualTo(9);
        assertThat(backend.slack()).isEqualTo(4);
        ScheduledTask docs = result.tasks().get(9);
        assertThat(docs.lf()).isEqualTo(34);
        assertThat(docs.slack()).isEqualTo(14);
    }

    @Test
    void cycleIsDetected() {
        List<TaskNode> cyclic = List.of(node(1, 2, 3), node(2, 2, 1), node(3, 2, 2));
        assertThatThrownBy(() -> calculator.calculate(cyclic))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("цикл");
    }
}
