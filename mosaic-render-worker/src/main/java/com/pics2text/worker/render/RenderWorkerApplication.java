package com.pics2text.worker.render;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.pics2text")
@EnableScheduling
@MapperScan("com.pics2text.job.infrastructure.persistence")
public class RenderWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RenderWorkerApplication.class, args);
    }
}
