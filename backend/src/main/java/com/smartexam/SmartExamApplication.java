package com.smartexam;

import com.smartexam.config.AiProperties;
import com.smartexam.config.CorsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智慧在线考试系统 - Spring Boot 主应用类
 *
 * 课程：Web程序设计课程设计（S3048I）
 * 组别：第二组
 * 项目：在线考试系统
 */
@SpringBootApplication
@EnableConfigurationProperties({AiProperties.class, CorsProperties.class})
@EnableAsync
@EnableScheduling
public class SmartExamApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartExamApplication.class, args);
    }
}
