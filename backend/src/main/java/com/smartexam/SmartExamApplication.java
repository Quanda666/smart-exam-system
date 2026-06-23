package com.smartexam;

import com.smartexam.config.AiProperties;
import com.smartexam.config.CorsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({AiProperties.class, CorsProperties.class})
@EnableAsync
@EnableScheduling
public class SmartExamApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartExamApplication.class, args);
    }
}
