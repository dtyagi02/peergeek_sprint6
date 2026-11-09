package com.peergeek.courselearning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CourseLearningServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourseLearningServiceApplication.class, args);
    }
}

