package com.pics2text.api.infrastructure.storage;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;

public interface ObjectStorageGateway {
    void ensureBucket();
    PresignedRequest presignPut(String key, String contentType, long contentLength);
    String createMultipart(String key, String contentType);
    PresignedRequest presignPart(String key, String uploadId, int partNumber);
    void completeMultipart(String key, String uploadId, List<CompletedUploadPart> parts);
    void abortMultipart(String key, String uploadId);
    StoredObject head(String key);
    List<UploadedPart> listParts(String key, String uploadId);
    InputStream open(String key);
    PresignedRequest presignGet(String key, String downloadName);
    void delete(String key);

    record PresignedRequest(URI url, String method, Map<String, List<String>> headers) {}
    record CompletedUploadPart(int partNumber, String eTag) {}
    record StoredObject(long contentLength, String contentType) {}
    record UploadedPart(int partNumber, long size) {}
}
