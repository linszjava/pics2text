package com.pics2text.worker.analysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.pics2text")
public class AnalysisWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalysisWorkerApplication.class, args);
    }
}

