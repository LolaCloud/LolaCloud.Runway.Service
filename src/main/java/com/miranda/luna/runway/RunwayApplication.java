package com.miranda.luna.runway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RunwayApplication {

    public static void main(String[] args) {
        SpringApplication.run(RunwayApplication.class, args);
    }
}
