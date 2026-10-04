package com.resplan.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-4: вход в систему, маркер доступа и ролевые ограничения */
class AuthApiTest extends ApiTestSupport {

    @Test
    void loginReturnsBearerTokenAndRole() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\": \"pm\", \"password\": \"resplan\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("PM"))
                .andExpect(jsonPath("$.user.fullName").value("Иванов Дмитрий"));
        getAs("ld", "/api/auth/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("ld"))
                .andExpect(jsonPath("$.role").value("LD"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\": \"pm\", \"password\": \"qwerty\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Неверные учетные данные"));
    }

    @Test
    void protectedResourcesRequireValidToken() throws Exception {
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/projects").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleRestrictionsAreEnforced() throws Exception {
        getAs("emp", "/api/conflicts").andExpect(status().isForbidden());
        getAs("pm", "/api/conflicts").andExpect(status().isForbidden());
        getAs("rm", "/api/conflicts").andExpect(status().isOk());
    }

    @Test
    void openApiDescriptionIsPublic() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths", hasKey("/api/auth/login")))
                .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"));
    }
}
