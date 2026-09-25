package com.pics2text.worker.render;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.pics2text")
public class RenderWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RenderWorkerApplication.class, args);
    }
}

