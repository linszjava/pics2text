package com.pics2text.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.pics2text")
@EnableScheduling
public class MosaicApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MosaicApiApplication.class, args);
    }
}
