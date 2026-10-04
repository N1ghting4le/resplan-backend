package com.resplan.scheduling;

import com.resplan.error.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Метод критического пути (CriticalPathCalculator)")
class CriticalPathCalculatorTest {

    private final CriticalPathCalculator calculator = new CriticalPathCalculator();

    private static TaskNode node(int id, int duration, Integer... preds) {
        return new TaskNode(id, "t" + id, duration, List.of(preds));
    }

    @Test
    @DisplayName("FR1-3: сетевой график проекта ATLAS – 34 рабочих дня и известный критический путь")
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
    @DisplayName("FR1-5: цикл в зависимостях задач обнаруживается")
    void cycleIsDetected() {
        List<TaskNode> cyclic = List.of(node(1, 2, 3), node(2, 2, 1), node(3, 2, 2));
        assertThatThrownBy(() -> calculator.calculate(cyclic))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("цикл");
    }

    @Test
    @DisplayName("FR1-5: зависимость задачи от самой себя считается циклом")
    void selfDependencyIsCycle() {
        assertThatThrownBy(() -> calculator.calculate(List.of(node(1, 3), node(2, 4, 2))))
                .isInstanceOf(CycleException.class)
                .hasFieldOrPropertyWithValue("code", "CYCLE");
    }

    @Test
    @DisplayName("FR1-3: параллельные ветви – резерв времени у некритической ветви")
    void parallelBranchesHaveSlack() {
        CpmResult result = calculator.calculate(List.of(node(1, 2), node(2, 6, 1), node(3, 3, 1), node(4, 1, 2, 3)));

        assertThat(result.duration()).isEqualTo(9);
        assertThat(result.criticalPath()).containsExactly(1, 2, 4);
        ScheduledTask shortBranch = result.tasks().get(2);
        assertThat(shortBranch.es()).isEqualTo(2);
        assertThat(shortBranch.ef()).isEqualTo(5);
        assertThat(shortBranch.ls()).isEqualTo(5);
        assertThat(shortBranch.lf()).isEqualTo(8);
        assertThat(shortBranch.critical()).isFalse();
        assertThat(result.tasks().get(3).critical()).isTrue();
    }

    @Test
    @DisplayName("FR1-3: пустой сетевой график имеет нулевую длительность")
    void emptyNetwork() {
        CpmResult result = calculator.calculate(List.of());

        assertThat(result.duration()).isZero();
        assertThat(result.tasks()).isEmpty();
        assertThat(result.criticalPath()).isEmpty();
    }

    @Test
    @DisplayName("FR1-3: ссылка на задачу вне графика игнорируется, вехи нулевой длительности допустимы")
    void unknownPredecessorIsIgnored() {
        CpmResult result = calculator.calculate(List.of(node(1, 4, 99), node(2, 0, 1)));

        assertThat(result.duration()).isEqualTo(4);
        assertThat(result.tasks().get(0).es()).isZero();
        assertThat(result.tasks().get(1).es()).isEqualTo(4);
        assertThat(result.criticalPath()).containsExactly(1, 2);
    }
}
