package com.resplan.matching;

import com.resplan.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static com.resplan.support.Fixtures.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Интеллектуальный подбор кандидатов (Smart Matching)")
class CandidateMatcherTest {

    private final List<ScoringCriterion> criteria = List.of(
            new SkillCriterion(), new GradeCriterion(), new AvailabilityCriterion(), new CostCriterion());
    private ResourceRequest request;
    private Project atlas;
    private AppUser pm;

    @BeforeEach
    void setUp() {
        pm = user(1, UserRole.PM);
        atlas = project(1, "ATLAS", pm, 1);
        // Senior, Java 4 и Spring 4, загрузка 50 %, две рабочие недели
        request = request(1, task(1, atlas, "Backend API", 10), SENIOR, 50, day(0), day(11))
                .requireSkill(JAVA, 4).requireSkill(SPRING, 4);
    }

    private MatchContext ctx(Employee e, Booking... bookings) {
        return new MatchContext(request, e, List.of(bookings));
    }

    @Nested
    @DisplayName("Критерии оценки (стратегии)")
    class Criteria {

        @Test
        @DisplayName("FR5-2: навыки – среднее отношение уровня к требуемому, не более 1")
        void skillScore() {
            Employee strong = employee(1).addSkill(JAVA, 5).addSkill(SPRING, 4);
            Employee partial = employee(2).addSkill(JAVA, 2);
            ResourceRequest noSkills = request(2, task(2, atlas, "Документация", 3), SENIOR, 50, day(0), day(4));

            assertThat(new SkillCriterion().score(ctx(strong))).isEqualTo(1.0);
            assertThat(new SkillCriterion().score(ctx(partial))).isEqualTo(0.25);
            assertThat(new SkillCriterion().score(new MatchContext(noSkills, partial, List.of()))).isEqualTo(1.0);
            assertThat(new SkillCriterion().threshold()).isEqualTo(0.30);
        }

        @ParameterizedTest(name = "грейд {0} при требуемом Senior -> {1}")
        @CsvSource({"1, 0.0", "2, 0.4", "3, 1.0", "4, 0.7"})
        @DisplayName("FR5-2: соответствие грейда")
        void gradeScore(int rank, double expected) {
            Grade grade = List.of(JUNIOR, MIDDLE, SENIOR, LEAD).get(rank - 1);

            assertThat(new GradeCriterion().score(ctx(employee(1, "Тестов", grade, 20)))).isEqualTo(expected);
        }

        @Test
        @DisplayName("FR5-2: доступность – доля требуемой загрузки, которую сотрудник может принять")
        void availabilityScore() {
            Employee e = employee(1);
            ProjectBooking half = projectBooking(1, e, atlas, BookingKind.HARD, day(0), day(11), 50);
            ProjectBooking busy = projectBooking(2, e, atlas, BookingKind.HARD, day(0), day(11), 80);
            ResourceRequest weekend = request(3, task(3, atlas, "Выходные", 1), SENIOR, 50, day(5), day(6));

            assertThat(new AvailabilityCriterion().score(ctx(e))).isEqualTo(1.0);
            assertThat(new AvailabilityCriterion().score(ctx(e, half))).isEqualTo(1.0);
            assertThat(new AvailabilityCriterion().score(ctx(e, busy))).isCloseTo(0.4, within(1e-9));
            assertThat(new AvailabilityCriterion().score(ctx(e, half, busy))).isZero();
            assertThat(new AvailabilityCriterion().score(new MatchContext(weekend, e, List.of()))).isZero();
        }

        @ParameterizedTest(name = "ставка {0} р./ч -> {1}")
        @CsvSource({"5, 1.0", "10, 1.0", "25, 0.5", "40, 0.0", "55, 0.0"})
        @DisplayName("FR5-2: стоимость – чем ниже ставка, тем выше оценка")
        void costScore(int rate, double expected) {
            assertThat(new CostCriterion().score(ctx(employee(1, "Тестов", SENIOR, rate))))
                    .isCloseTo(expected, within(1e-9));
        }
    }

    @Test
    @DisplayName("Сумма весов критериев должна быть равна 1")
    void weightsMustSumToOne() {
        assertThatThrownBy(() -> new CandidateMatcher(List.of(new SkillCriterion(), new GradeCriterion())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("0.7");
        assertThatNoException().isThrownBy(() -> new CandidateMatcher(criteria));
    }

    @Test
    @DisplayName("FR5-2: кандидаты ранжируются по проценту совпадения, при равенстве – по ставке")
    void candidatesAreRankedByMatchThenRate() {
        Employee lead = employee(1, "Чен", LEAD, 38).addSkill(JAVA, 5).addSkill(SPRING, 5);
        Employee senior = employee(2, "Ибраев", SENIOR, 25).addSkill(JAVA, 5).addSkill(SPRING, 5);
        Employee cheapTwin = employee(3, "Двойник", SENIOR, 10).addSkill(JAVA, 5).addSkill(SPRING, 5);
        Employee expensiveTwin = employee(4, "Двойник2", SENIOR, 11).addSkill(JAVA, 5).addSkill(SPRING, 5);

        List<Candidate> ranked = new CandidateMatcher(criteria)
                .rank(request, List.of(lead, expensiveTwin, senior, cheapTwin), Map.of());

        assertThat(ranked).extracting(c -> c.employee().getLastName())
                .containsExactly("Двойник", "Двойник2", "Ибраев", "Чен");
        Candidate best = ranked.get(0);
        assertThat(best.match()).isEqualTo(100);
        assertThat(best.scores()).containsOnlyKeys("skills", "grade", "availability", "cost");
        // 0,5·1 + 0,2·0,7 + 0,25·1 + 0,05·(1 − 28/30) = 0,8933 -> 89 %
        assertThat(ranked.get(3).match()).isEqualTo(89);
    }

    @Test
    @DisplayName("FR5-2: кандидат ниже порога по навыкам (0,3) отсеивается")
    void unqualifiedCandidateIsFiltered() {
        Employee frontend = employee(1, "Новик", SENIOR, 26).addSkill(JAVA, 2);

        assertThat(new CandidateMatcher(criteria).rank(request, List.of(frontend), Map.of())).isEmpty();
    }

    @Test
    @DisplayName("FR9-3: сотрудник с защищенным обучением в периоде запроса исключается из подбора")
    void protectedTrainingExcludesEmployee() {
        Employee belova = employee(1, "Белова", SENIOR, 30).addSkill(JAVA, 5).addSkill(SPRING, 5);
        Employee kovalev = employee(2, "Ковалёв", SENIOR, 22).addSkill(JAVA, 5).addSkill(SPRING, 5);
        Map<Integer, List<Booking>> busy = Map.of(
                1, List.of(training(1, belova, day(9), day(9), 100, true)),
                2, List.of(training(2, kovalev, day(9), day(9), 100, false),
                        training(3, kovalev, day(20), day(24), 100, true)));

        List<Candidate> ranked = new CandidateMatcher(criteria).rank(request, List.of(belova, kovalev), busy);

        assertThat(ranked).extracting(c -> c.employee().getLastName()).containsExactly("Ковалёв");
    }

    @Test
    @DisplayName("FR3-3: кандидат, отклоненный PM по запросу, повторно не предлагается")
    void rejectedCandidateIsExcluded() {
        Employee kovalev = employee(2, "Ковалёв", SENIOR, 22).addSkill(JAVA, 5).addSkill(SPRING, 5);
        request.proposeCandidate(projectBooking(1, kovalev, atlas, BookingKind.SOFT, day(0), day(11), 50));
        request.rejectCandidate("Не прошел собеседование", pm);

        assertThat(new CandidateMatcher(criteria).rank(request, List.of(kovalev), Map.of())).isEmpty();
    }
}
