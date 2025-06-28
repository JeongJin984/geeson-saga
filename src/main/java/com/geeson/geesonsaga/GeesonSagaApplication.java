package com.geeson.geesonsaga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.statemachine.config.EnableStateMachineFactory;

@SpringBootApplication
public class GeesonSagaApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeesonSagaApplication.class, args);
    }

}
