package com.resplan.domain;

import com.resplan.error.AccessDeniedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;

import static com.resplan.support.Fixtures.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Правила сущностей Project, Employee, Task, AppUser, Notification")
class DomainRulesTest {

    private final AppUser pm = user(1, UserRole.PM);
    private final AppUser otherPm = user(2, UserRole.PM);
    private final Project atlas = project(1, "ATLAS", pm, 1);

    @Test
    @DisplayName("FR1-4: изменять проект может только его PM")
    void onlyOwnPmManagesProject() {
        assertThat(atlas.isManagedBy(pm)).isTrue();
        assertThatNoException().isThrownBy(() -> atlas.requireManagedBy(pm, "Изменять сетевой график"));
        assertThatThrownBy(() -> atlas.requireManagedBy(otherPm, "Изменять сетевой график"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Изменять сетевой график может только PM проекта ATLAS");
        assertThat(atlas.getStatus()).isEqualTo(ProjectStatus.PLANNING);
    }

    @Test
    @DisplayName("FR1-2: изменение задачи заменяет связи целиком, удаление предшественника")
    void taskUpdateReplacesDependencies() {
        Task analysis = task(1, atlas, "Анализ требований", 5);
        Task design = task(2, atlas, "Проектирование архитектуры", 4, analysis);
        Task datasets = task(3, atlas, "Подготовка датасетов", 6);

        design.update("Архитектура", 6, List.of(datasets));

        assertThat(design.getName()).isEqualTo("Архитектура");
        assertThat(design.getDurationDays()).isEqualTo((short) 6);
        assertThat(design.getPredecessors()).containsExactly(datasets);
        design.removePredecessor(datasets);
        assertThat(design.getPredecessors()).isEmpty();
    }

    @Test
    @DisplayName("FR3-1: уровень навыка сотрудника; отсутствующий навык – уровень 0")
    void employeeSkillLevel() {
        Employee kovalev = employee(1, "Ковалёв", MIDDLE, 22).addSkill(JAVA, 4).addSkill(SPRING, 3);

        assertThat(kovalev.levelOf(JAVA)).isEqualTo(4);
        assertThat(kovalev.levelOf(SPRING)).isEqualTo(3);
        assertThat(kovalev.levelOf(LLM)).isZero();
        assertThat(kovalev.fullName()).isEqualTo("Ковалёв Тест");
    }

    @ParameterizedTest(name = "уровень {0}")
    @ValueSource(ints = {0, 6, -1})
    @DisplayName("FR2-2: уровень навыка должен быть от 1 до 5")
    void skillLevelRange(int level) {
        assertThatThrownBy(() -> new SkillLevel(JAVA, level)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("FR4-3: успешный вход фиксируется в учетной записи, роль проверяется")
    void userRecordsLoginAndRole() {
        Instant at = Instant.parse("2026-10-05T07:00:00Z");

        pm.recordLogin(at);

        assertThat(pm.getLastLoginAt()).isEqualTo(at);
        assertThat(pm.hasRole(UserRole.PM)).isTrue();
        assertThat(pm.hasRole(UserRole.RM)).isFalse();
        assertThat(pm.isActive()).isTrue();
    }

    @Test
    @DisplayName("FR6-3: уведомление адресовано конкретному пользователю и отмечается прочитанным")
    void notificationBelongsToRecipient() {
        Notification n = new Notification(pm, "Конфликт №1 разрешен");

        assertThat(n.isAddressedTo(pm)).isTrue();
        assertThat(n.isAddressedTo(otherPm)).isFalse();
        assertThat(n.isRead()).isFalse();
        n.markRead();
        assertThat(n.isRead()).isTrue();
    }
}
