package com.resplan.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-6 и UC-7: ресурсные конфликты и управление бронированием */
class BookingAndConflictApiTest extends ApiTestSupport {

    private int bookingId(String lastName, String project, String kind) throws Exception {
        List<Integer> ids = read(getAs("rm", "/api/bookings?employeeId={e}&from=2026-10-01&to=2026-12-31",
                employeeId(lastName)), "$[?(@.title == '" + project + "' && @.kind == '" + kind + "')].id");
        return ids.get(0);
    }

    @Test
    void conflictPanelShowsOpenConflictsWithPriorities() throws Exception {
        getAs("rm", "/api/conflicts")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].employee").value("Ковалёв Алексей"))
                .andExpect(jsonPath("$[0].totalLoad").value(150))
                .andExpect(jsonPath("$[0].first.projectPriority").value(1))
                .andExpect(jsonPath("$[0].second.projectPriority").value(2));
    }

    @Test
    void resolutionKeepsOneBookingReleasesOtherAndNotifiesPm() throws Exception {
        int atlas = bookingId("Ковалёв", "ATLAS", "HARD");
        int orion = bookingId("Ковалёв", "ORION", "HARD");
        postAs("rm", "/api/conflicts/1/resolution", "{\"keepBookingId\": " + atlas + ", \"note\": \"Приоритет ATLAS выше\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionNote").value("Приоритет ATLAS выше"));
        getAs("rm", "/api/bookings/{id}", orion).andExpect(jsonPath("$.active").value(false));
        getAs("pm", "/api/notifications").andExpect(jsonPath("$[0].message", containsString("разрешен")));
        getAs("emp", "/api/notifications").andExpect(jsonPath("$[0].message", containsString("ORION")));
        getAs("rm", "/api/conflicts").andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void escalatedConflictCannotBeResolvedAgain() throws Exception {
        postAs("rm", "/api/conflicts/2/escalation", "{\"note\": \"\"}").andExpect(status().isBadRequest());
        postAs("rm", "/api/conflicts/2/escalation", "{\"note\": \"Оба проекта критичны для заказчика\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESCALATED"));
        postAs("rm", "/api/conflicts/2/resolution", "{\"keepBookingId\": " + bookingId("Зелински", "ORION", "HARD") + "}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONFLICT_CLOSED"));
        postAs("rm", "/api/conflicts/1/resolution", "{\"keepBookingId\": 5}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BOOKING"));
    }

    @Test
    void softBookingCanBeHardened() throws Exception {
        int soft = bookingId("Алиев", "ORION", "SOFT");
        patchAs("rm", "/api/bookings/" + soft, "{\"kind\": \"HARD\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.kind").value("HARD"))
                .andExpect(jsonPath("$.conflicts", hasSize(0)));
        patchAs("rm", "/api/bookings/" + soft, "{\"kind\": \"SOFT\"}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("KIND"));
    }

    @Test
    void bookingLinkedToRequestIsEditedOnlyThroughRequest() throws Exception {
        int linked = bookingId("Алиев", "ATLAS", "SOFT");
        patchAs("rm", "/api/bookings/" + linked, "{\"kind\": \"HARD\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_LINKED"));
    }

    @Test
    void hardBookingWithOverloadRegistersConflict() throws Exception {
        String body = """
                {"employeeId": %d, "projectId": 1, "kind": "HARD",
                 "startDate": "2026-10-12", "endDate": "2026-10-16", "loadPercent": 100}""".formatted(employeeId("Лис"));
        postAs("rm", "/api/bookings", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.booking.title").value("ATLAS"))
                .andExpect(jsonPath("$.warnings", hasSize(1)))
                .andExpect(jsonPath("$.conflicts[0].totalLoad").value(150));
        postAs("pm", "/api/bookings", body).andExpect(status().isForbidden());
    }

    @Test
    void bookingRulesAreChecked() throws Exception {
        String reversed = """
                {"employeeId": 1, "projectId": 1, "kind": "HARD",
                 "startDate": "2026-11-18", "endDate": "2026-11-16", "loadPercent": 50}""";
        postAs("rm", "/api/bookings", reversed)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.periodValid").exists());
        String duringTraining = """
                {"employeeId": %d, "projectId": 1, "kind": "SOFT",
                 "startDate": "2026-11-16", "endDate": "2026-11-18", "loadPercent": 50}""".formatted(employeeId("Ковалёв"));
        postAs("rm", "/api/bookings", duringTraining)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PROTECTED_TIME"));
    }

    @Test
    void releasingApprovedBookingReturnsRequestToSearch() throws Exception {
        int bookingId = read(postAs("pm", "/api/requests/1/approval", "").andExpect(status().isOk()),
                "$.request.bookingId");
        deleteAs("rm", "/api/bookings/" + bookingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.active").value(false))
                .andExpect(jsonPath("$.returnedRequestId").value(1));
        getAs("pm", "/api/requests/1").andExpect(jsonPath("$.status").value("SEARCHING"));
        getAs("pm", "/api/projects/1/tasks").andExpect(jsonPath("$[4].assignee").doesNotExist());
        getAs("pm", "/api/notifications").andExpect(jsonPath("$[0].message", containsString("возвращен на подбор")));
        deleteAs("rm", "/api/bookings/" + bookingId)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("RELEASED"));
    }

    @Test
    void benchListsEmployeesWithoutBookings() throws Exception {
        getAs("rm", "/api/employees?date=2026-10-05&bench=true")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Юсупова Карина")))
                .andExpect(jsonPath("$[*].name", not(hasItem("Ткач Анна"))));
        getAs("rm", "/api/employees/{id}?date=2026-10-20", employeeId("Ковалёв"))
                .andExpect(jsonPath("$.loadPercent").value(150))
                .andExpect(jsonPath("$.skills.Java").value(4))
                .andExpect(jsonPath("$.bookings", hasSize(2)));
    }
}
