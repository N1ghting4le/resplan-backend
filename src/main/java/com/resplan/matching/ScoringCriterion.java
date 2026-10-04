package com.resplan.matching;

/**
 * Критерий оценки кандидата (паттерн «Стратегия»).
 * Итоговая оценка – взвешенная сумма оценок всех критериев.
 */
public interface ScoringCriterion {

    String code();

    /** Вес критерия; сумма весов всех критериев равна 1 */
    double weight();

    /** Оценка соответствия кандидата в диапазоне [0; 1] */
    double score(MatchContext ctx);

    /** Минимальная допустимая оценка: кандидат с меньшей оценкой отсеивается */
    default double threshold() {
        return 0;
    }
}
