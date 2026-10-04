package com.resplan.api;

import com.jayway.jsonpath.JsonPath;
import com.resplan.config.DemoData;
import com.resplan.repository.EmployeeRepository;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Общая основа интеграционных тестов REST API: вход под демонстрационными учетными записями */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Transactional
abstract class ApiTestSupport {

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected EmployeeRepository employees;

    private final Map<String, String> tokens = new HashMap<>();

    protected String token(String login) throws Exception {
        if (!tokens.containsKey(login)) {
            String body = "{\"login\": \"" + login + "\", \"password\": \"" + DemoData.DEMO_PASSWORD + "\"}";
            String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            tokens.put(login, JsonPath.read(json, "$.accessToken"));
        }
        return tokens.get(login);
    }

    protected ResultActions as(String login, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(login)));
    }

    protected ResultActions getAs(String login, String url, Object... vars) throws Exception {
        return as(login, get(url, vars));
    }

    protected ResultActions postAs(String login, String url, String body) throws Exception {
        return as(login, post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions putAs(String login, String url, String body) throws Exception {
        return as(login, put(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions patchAs(String login, String url, String body) throws Exception {
        return as(login, patch(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions deleteAs(String login, String url) throws Exception {
        return as(login, delete(url));
    }

    protected int employeeId(String lastName) {
        return employees.findByLastName(lastName).orElseThrow().getId();
    }

    protected static <T> T read(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), path);
    }
}
