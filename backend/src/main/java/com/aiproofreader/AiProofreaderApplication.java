package com.aiproofreader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiProofreaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiProofreaderApplication.class, args);
    }
}
