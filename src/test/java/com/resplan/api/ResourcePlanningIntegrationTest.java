package com.resplan.api;

import com.resplan.domain.BookingKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-2, UC-3, UC-5: запросы на ресурс, подбор, утверждение и отклонение кандидатов */
@DisplayName("Интеграционные тесты REST API: UC-2, UC-3, UC-5 запросы и подбор")
class ResourcePlanningIntegrationTest extends ApiTestSupport {

    private static final int R1 = 1, R2 = 2, R3 = 3;

    @Test
    void matchingRanksQaCandidatesAndSkipsUnqualified() throws Exception {
        getAs("rm", "/api/requests/{id}/candidates", R2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Ткач Анна", "Юсупова Карина", "Лис Сергей")))
                .andExpect(jsonPath("$[0].match").value(86))
                .andExpect(jsonPath("$[0].scores.availability").value(0.5));
    }

    @Test
    void matchingExcludesEmployeesWithProtectedTraining() throws Exception {
        String body = """
                {"taskId": 5, "role": "ML-инженер (GenAI)", "grade": "Senior", "loadPercent": 100,
                 "startDate": "2026-10-20", "endDate": "2026-11-04",
                 "skills": {"Python": 4, "LLM": 4, "RAG": 3}}""";
        var created = postAs("pm", "/api/requests", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SEARCHING"))
                .andExpect(jsonPath("$.skills.LLM").value(4));
        int id = read(created, "$.id");
        getAs("rm", "/api/requests/{id}/candidates", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Алиев Тимур")))
                .andExpect(jsonPath("$[*].name", not(hasItem("Белова Ирина"))))
                .andExpect(jsonPath("$[*].name", not(hasItem("Хасан Омар"))));
    }

    @Test
    void submitRequiresProjectManagerOfProject() throws Exception {
        String body = """
                {"taskId": 5, "role": "ML", "grade": "Senior", "loadPercent": 100,
                 "startDate": "2026-10-20", "endDate": "2026-11-04", "skills": {"Python": 4}}""";
        postAs("pm2", "/api/requests", body).andExpect(status().isForbidden());
        postAs("rm", "/api/requests", body).andExpect(status().isForbidden());
    }

    @Test
    void submitValidatesFieldsAndPeriod() throws Exception {
        String body = """
                {"taskId": 5, "role": "", "grade": "Senior", "loadPercent": 120,
                 "startDate": "2026-11-04", "endDate": "2026-10-20", "skills": {}}""";
        postAs("pm", "/api/requests", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.role").exists())
                .andExpect(jsonPath("$.errors.loadPercent").exists())
                .andExpect(jsonPath("$.errors.skills").exists())
                .andExpect(jsonPath("$.errors.periodValid").value("Дата окончания раньше даты начала"));
    }

    @Test
    void requestListIsFilteredByRoleAndStatus() throws Exception {
        getAs("rm", "/api/requests").andExpect(jsonPath("$", hasSize(3)));
        getAs("rm", "/api/requests?status=SEARCHING").andExpect(jsonPath("$[*].id", contains(R2, R3)));
        getAs("pm2", "/api/requests").andExpect(jsonPath("$[*].project", contains("HELIX")));
        getAs("emp", "/api/requests").andExpect(status().isForbidden());
    }

    @Test
    void approvalTurnsSoftBookingIntoHardWithoutConflicts() throws Exception {
        postAs("pm", "/api/requests/" + R1 + "/approval", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.status").value("APPROVED"))
                .andExpect(jsonPath("$.request.candidate").value("Алиев Тимур"))
                .andExpect(jsonPath("$.request.bookingKind").value(BookingKind.HARD.name()))
                .andExpect(jsonPath("$.conflicts", hasSize(0)));
        getAs("rm", "/api/notifications")
                .andExpect(jsonPath("$[0].message", containsString("утвержден по запросу №1")));
    }

    @Test
    void approvalIsAllowedOnlyOnceAndOnlyForProjectManager() throws Exception {
        postAs("rm", "/api/requests/" + R1 + "/approval", "").andExpect(status().isForbidden());
        postAs("pm2", "/api/requests/" + R1 + "/approval", "").andExpect(status().isForbidden());
        postAs("pm", "/api/requests/" + R1 + "/approval", "").andExpect(status().isOk());
        postAs("pm", "/api/requests/" + R1 + "/approval", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATUS"));
    }

    @Test
    void proposalDuringProtectedTrainingIsRejected() throws Exception {
        postAs("rm", "/api/requests/" + R2 + "/proposal", "{\"employeeId\": " + employeeId("Ковалёв") + "}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PROTECTED_TIME"))
                .andExpect(jsonPath("$.detail", containsString("Spring AI")));
    }

    @Test
    void approvalWithOverloadRegistersResourceConflict() throws Exception {
        postAs("rm", "/api/requests/" + R3 + "/proposal", "{\"employeeId\": " + employeeId("Зелински") + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PM"))
                .andExpect(jsonPath("$.warnings", hasSize(1)));
        postAs("pm2", "/api/requests/" + R3 + "/approval", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.status").value("APPROVED"))
                .andExpect(jsonPath("$.conflicts", hasSize(1)))
                .andExpect(jsonPath("$.conflicts[0].bookingA").value("ORION"))
                .andExpect(jsonPath("$.conflicts[0].totalLoad").value(130))
                .andExpect(jsonPath("$.conflicts[0].from").value("2026-10-12"))
                .andExpect(jsonPath("$.conflicts[0].to").value("2026-11-30"));
    }

    @Test
    void rejectionRequiresReasonAndExcludesCandidateFromMatching() throws Exception {
        postAs("pm", "/api/requests/" + R1 + "/rejection", "{\"reason\": \"\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REASON"));
        postAs("pm", "/api/requests/" + R1 + "/rejection", "{\"reason\": \"Нет опыта с RAG\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEARCHING"))
                .andExpect(jsonPath("$.candidate").doesNotExist());
        getAs("rm", "/api/requests/{id}/candidates", R1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", not(hasItem("Алиев Тимур"))));
    }

    @Test
    void cancelledRequestReleasesBookingAndIsFinal() throws Exception {
        deleteAs("pm", "/api/requests/" + R1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.bookingId").doesNotExist());
        postAs("pm", "/api/requests/" + R1 + "/approval", "").andExpect(status().isConflict());
    }

    @Test
    void externalHireMovesRequestOutOfSearch() throws Exception {
        postAs("rm", "/api/requests/" + R2 + "/external-hire", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXTERNAL_HIRE"));
    }
}
