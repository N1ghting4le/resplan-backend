package com.resplan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Ссылки на образовательные порталы, которые показываются сотруднику в резерве (FR8-3) */
@ConfigurationProperties(prefix = "resplan.learning")
public record LearningProperties(List<String> portals) {
}
