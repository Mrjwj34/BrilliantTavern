package com.github.jwj.brilliantavern;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BrilliantTavernApplication {

    public static void main(String[] args) {
        SpringApplication.run(BrilliantTavernApplication.class, args);
    }

}

