package com.rsgcs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RsgcsApplication {

    public static void main(String[] args) {
        SpringApplication.run(RsgcsApplication.class, args);
    }
}
