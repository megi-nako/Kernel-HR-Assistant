package com.kernel.hr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.kernel.hr.config.AppProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class HrAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(HrAssistantApplication.class, args);
    }
}
