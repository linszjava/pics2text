package com.pics2text.api.infrastructure.storage;

import com.pics2text.api.config.ObjectStorageProperties;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Comparator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

@Component
public class S3ObjectStorageGateway implements ObjectStorageGateway {
    private final S3Client client;
    private final S3Presigner presigner;
    private final ObjectStorageProperties properties;

    public S3ObjectStorageGateway(S3Client client, S3Presigner presigner, ObjectStorageProperties properties) {
        this.client = client;
        this.presigner = presigner;
        this.properties = properties;
    }

    @PostConstruct
    public void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) throw exception;
            client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
        }
    }

    @Override
    public PresignedRequest presignPut(String key, String contentType, long contentLength) {
        PutObjectRequest request = PutObjectRequest.builder().bucket(properties.bucket()).key(key)
                .contentType(contentType).contentLength(contentLength).build();
        var signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(properties.presignDuration()).putObjectRequest(request).build());
        return map(signed);
    }

    @Override
    public String createMultipart(String key, String contentType) {
        return client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                .bucket(properties.bucket()).key(key).contentType(contentType).build()).uploadId();
    }

    @Override
    public PresignedRequest presignPart(String key, String uploadId, int partNumber) {
        UploadPartRequest request = UploadPartRequest.builder().bucket(properties.bucket()).key(key)
                .uploadId(uploadId).partNumber(partNumber).build();
        var signed = presigner.presignUploadPart(UploadPartPresignRequest.builder()
                .signatureDuration(properties.presignDuration()).uploadPartRequest(request).build());
        return map(signed);
    }

    @Override
    public void completeMultipart(String key, String uploadId, List<CompletedUploadPart> parts) {
        List<CompletedPart> completed = parts.stream()
                .sorted(Comparator.comparingInt(CompletedUploadPart::partNumber))
                .map(part -> CompletedPart.builder()
                .partNumber(part.partNumber()).eTag(part.eTag()).build()).toList();
        client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(properties.bucket()).key(key).uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completed).build()).build());
    }

    @Override
    public void abortMultipart(String key, String uploadId) {
        client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                .bucket(properties.bucket()).key(key).uploadId(uploadId).build());
    }

    @Override
    public StoredObject head(String key) {
        var response = client.headObject(HeadObjectRequest.builder().bucket(properties.bucket()).key(key).build());
        return new StoredObject(response.contentLength(), response.contentType());
    }

    @Override
    public List<UploadedPart> listParts(String key, String uploadId) {
        return client.listParts(ListPartsRequest.builder().bucket(properties.bucket()).key(key)
                        .uploadId(uploadId).build())
                .parts().stream().map(part -> new UploadedPart(part.partNumber(), part.size())).toList();
    }

    @Override
    public InputStream open(String key) {
        return client.getObject(GetObjectRequest.builder().bucket(properties.bucket()).key(key).build());
    }

    @Override
    public PresignedRequest presignGet(String key, String downloadName) {
        String disposition = "attachment; filename*=UTF-8''" + encode(downloadName);
        GetObjectRequest request = GetObjectRequest.builder().bucket(properties.bucket()).key(key)
                .responseContentDisposition(disposition).build();
        var signed = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(properties.presignDuration()).getObjectRequest(request).build());
        return map(signed);
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
    }

    private PresignedRequest map(software.amazon.awssdk.awscore.presigner.PresignedRequest signed) {
        Map<String, List<String>> clientHeaders = new LinkedHashMap<>(signed.signedHeaders());
        clientHeaders.remove("host");
        clientHeaders.remove("content-length");
        return new PresignedRequest(java.net.URI.create(signed.url().toExternalForm()), signed.httpRequest().method().name(),
                Map.copyOf(clientHeaders));
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
