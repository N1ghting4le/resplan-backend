package com.resplan.api;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-8 и UC-9: персональный график загрузки и защищенное время на обучение */
class CalendarAndTrainingApiTest extends ApiTestSupport {

    @Test
    void myCalendarShowsMonthLoadByWorkdays() throws Exception {
        getAs("emp", "/api/me/calendar?period=MONTH&date=2026-10-15")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee").value("Ковалёв Алексей"))
                .andExpect(jsonPath("$.from").value("2026-10-01"))
                .andExpect(jsonPath("$.to").value("2026-10-31"))
                .andExpect(jsonPath("$.workdays").value(22))
                .andExpect(jsonPath("$.items[*].title", contains("ATLAS", "ORION")))
                .andExpect(jsonPath("$.peakLoad").value(150))
                .andExpect(jsonPath("$.bench").value(false))
                .andExpect(jsonPath("$.hints", empty()));
    }

    @Test
    void emptyCalendarMeansBenchWithLearningLinks() throws Exception {
        getAs("emp2", "/api/me/calendar?period=WEEK&date=2026-10-07")
                .andExpect(jsonPath("$.from").value("2026-10-05"))
                .andExpect(jsonPath("$.to").value("2026-10-11"))
                .andExpect(jsonPath("$.bench").value(true))
                .andExpect(jsonPath("$.hints", not(empty())));
    }

    @Test
    void otherEmployeeCalendarIsForManagersOnly() throws Exception {
        getAs("emp", "/api/employees/2/calendar").andExpect(status().isForbidden());
        getAs("ld", "/api/employees/2/calendar?period=QUARTER&date=2026-11-01")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-10-01"))
                .andExpect(jsonPath("$.to").value("2026-12-31"));
    }

    @Test
    void protectedTrainingCannotOverlapHardBooking() throws Exception {
        String body = """
                {"employeeId": %d, "courseTitle": "Kubernetes для разработчиков", "startDate": "2026-10-20",
                 "endDate": "2026-10-22", "loadPercent": 100, "protectedTime": true}""".formatted(employeeId("Ковалёв"));
        postAs("ld", "/api/trainings", body)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PROTECTED_TIME"))
                .andExpect(jsonPath("$.detail", containsString("выберите другие даты")));
    }

    @Test
    void protectedTrainingExcludesEmployeeFromMatchingUntilCancelled() throws Exception {
        String body = """
                {"employeeId": %d, "courseTitle": "ISTQB Advanced Test Automation Engineer", "provider": "ISTQB",
                 "startDate": "2026-11-09", "endDate": "2026-11-13", "loadPercent": 100, "protectedTime": true}"""
                .formatted(employeeId("Юсупова"));
        var created = postAs("ld", "/api/trainings", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.booking.type").value("TRAINING"))
                .andExpect(jsonPath("$.booking.protectedTime").value(true));
        int id = read(created, "$.booking.id");
        getAs("emp", "/api/courses").andExpect(jsonPath("$[*].title", hasItem("ISTQB Advanced Test Automation Engineer")));
        getAs("rm", "/api/requests/2/candidates").andExpect(jsonPath("$[*].name", not(hasItem("Юсупова Карина"))));
        getAs("emp2", "/api/notifications").andExpect(jsonPath("$[0].message", containsString("ISTQB")));

        deleteAs("ld", "/api/trainings/" + id).andExpect(status().isNoContent());
        getAs("rm", "/api/requests/2/candidates").andExpect(jsonPath("$[*].name", hasItem("Юсупова Карина")));
    }

    @Test
    void onlyLdReservesTraining() throws Exception {
        String body = """
                {"employeeId": 1, "courseTitle": "Курс", "startDate": "2026-12-01",
                 "endDate": "2026-12-02", "loadPercent": 100, "protectedTime": false}""";
        postAs("emp", "/api/trainings", body).andExpect(status().isForbidden());
        postAs("rm", "/api/trainings", body).andExpect(status().isForbidden());
    }

    @Test
    void notificationCanBeMarkedRead() throws Exception {
        postAs("pm", "/api/requests/1/approval", "").andExpect(status().isOk());
        int id = read(getAs("rm", "/api/notifications?unread=true"), "$[0].id");
        patchAs("rm", "/api/notifications/" + id, "{\"read\": true}").andExpect(jsonPath("$.read").value(true));
        getAs("rm", "/api/notifications?unread=true").andExpect(jsonPath("$[*].id", not(hasItem(id))));
        patchAs("pm", "/api/notifications/" + id, "{\"read\": true}").andExpect(status().isNotFound());
    }
}
