package com.github.darekdan.demoredislettuce;

import org.springframework.boot.SpringApplication;

public class TestDemoredisLettuceApplication {

    public static void main(String[] args) {
        SpringApplication
                .from(DemoredisLettuceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }

}
