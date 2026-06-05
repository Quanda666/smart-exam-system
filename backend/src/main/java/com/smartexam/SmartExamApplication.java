package com.smartexam;

import com.smartexam.config.AiProperties;
import com.smartexam.config.CorsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AiProperties.class, CorsProperties.class})
public class SmartExamApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartExamApplication.class, args);
    }
}
