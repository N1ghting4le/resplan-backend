package com.resplan.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-1: проекты, задачи сетевого графика и пересчет критического пути */
@DisplayName("Интеграционные тесты REST API: UC-1 сетевой график")
class ProjectPlanApiTest extends ApiTestSupport {

    private static final int ATLAS = 1;

    @Test
    void projectListDependsOnRole() throws Exception {
        getAs("pm", "/api/projects").andExpect(jsonPath("$[*].code", contains("ATLAS", "ORION")));
        getAs("pm2", "/api/projects").andExpect(jsonPath("$[*].code", contains("HELIX")));
        getAs("rm", "/api/projects").andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void scheduleOfAtlasUsesCriticalPathMethod() throws Exception {
        getAs("emp", "/api/projects/{id}/schedule", ATLAS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationDays").value(34))
                .andExpect(jsonPath("$.criticalPath", contains("Анализ требований", "Подготовка датасетов",
                        "RAG-модуль GenAI", "Интеграция модулей", "Системное тестирование", "Развертывание у заказчика")))
                .andExpect(jsonPath("$.finish").value("2026-11-19"));
    }

    @Test
    void changingDurationRecalculatesCriticalPath() throws Exception {
        putAs("pm", "/api/projects/1/tasks/10",
                "{\"name\": \"Пользовательская документация\", \"durationDays\": 30, \"predecessorIds\": [6]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationDays").value(30));
        getAs("pm", "/api/projects/{id}/schedule", ATLAS)
                .andExpect(jsonPath("$.durationDays").value(47))
                .andExpect(jsonPath("$.criticalPath", contains("Анализ требований", "Проектирование архитектуры",
                        "Web-интерфейс оператора", "Пользовательская документация")));
    }

    /** Без общей тестовой транзакции: проверяется настоящий откат транзакции сервиса */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cyclicDependencyIsRejectedAndRolledBack() throws Exception {
        putAs("pm", "/api/projects/1/tasks/1", "{\"name\": \"Анализ требований\", \"durationDays\": 5, \"predecessorIds\": [9]}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CYCLE"));
        getAs("pm", "/api/projects/{id}/tasks", ATLAS).andExpect(jsonPath("$[0].predecessorIds", empty()));
    }

    @Test
    void taskIsAddedValidatedAndDeleted() throws Exception {
        var created = postAs("pm", "/api/projects/1/tasks",
                "{\"name\": \"Нагрузочное тестирование\", \"durationDays\": 4, \"predecessorIds\": [7]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.predecessorIds", contains(7)));
        int id = read(created, "$.id");
        getAs("pm", "/api/projects/{id}/schedule", ATLAS).andExpect(jsonPath("$.tasks", hasSize(11)));

        postAs("pm", "/api/projects/1/tasks", "{\"name\": \"нагрузочное тестирование\", \"durationDays\": 1}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("DUPLICATE"));
        postAs("pm", "/api/projects/1/tasks", "{\"name\": \"Миграция\", \"durationDays\": 1, \"predecessorIds\": [11]}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("DEPENDENCY"));
        postAs("pm", "/api/projects/1/tasks", "{\"name\": \"Миграция\", \"durationDays\": -1}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.durationDays").exists());

        deleteAs("pm", "/api/projects/1/tasks/" + id).andExpect(status().isNoContent());
        getAs("pm", "/api/projects/{id}/tasks", ATLAS).andExpect(jsonPath("$", hasSize(10)));
    }

    @Test
    void taskWithRequestsCannotBeDeletedAndOnlyOwnPmEdits() throws Exception {
        deleteAs("pm", "/api/projects/1/tasks/5")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("TASK_IN_USE"));
        deleteAs("pm2", "/api/projects/1/tasks/9").andExpect(status().isForbidden());
        deleteAs("rm", "/api/projects/1/tasks/9").andExpect(status().isForbidden());
    }
}
