package com.pics2text.worker.analysis;

import jakarta.annotation.PostConstruct;
import nu.pattern.OpenCV;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@EnableConfigurationProperties({AnalysisProperties.class, AnalysisStorageProperties.class})
public class AnalysisConfiguration {
    private static volatile boolean openCvLoaded;

    @PostConstruct
    void loadOpenCv() {
        if (!openCvLoaded) {
            synchronized (AnalysisConfiguration.class) {
                if (!openCvLoaded) {
                    OpenCV.loadLocally();
                    openCvLoaded = true;
                }
            }
        }
    }

    @Bean(destroyMethod = "close")
    S3Client analysisS3Client(AnalysisStorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(properties.endpoint())
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }
}
