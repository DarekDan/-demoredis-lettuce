package com.github.darekdan.demoredislettuce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class DemoredisLettuceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoredisLettuceApplication.class, args);
    }

}
