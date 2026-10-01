package com.pics2text.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.pics2text")
@EnableScheduling
@MapperScan({"com.pics2text.api.infrastructure.persistence", "com.pics2text.job.infrastructure.persistence",
        "com.pics2text.photoasset.infrastructure.persistence", "com.pics2text.layout.infrastructure.persistence",
        "com.pics2text.surface.infrastructure.persistence", "com.pics2text.rendering.infrastructure.persistence"})
public class MosaicApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MosaicApiApplication.class, args);
    }
}
