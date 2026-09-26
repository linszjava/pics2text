package com.pics2text.worker.analysis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.pics2text")
@EnableScheduling
@MapperScan({"com.pics2text.job.infrastructure.persistence",
        "com.pics2text.photoasset.infrastructure.persistence"})
public class AnalysisWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalysisWorkerApplication.class, args);
    }
}
