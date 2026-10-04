package com.resplan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ResPlanApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResPlanApplication.class, args);
    }
}
