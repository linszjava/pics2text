package com.pics2text.api.config;

import com.pics2text.api.common.ApiException;
import com.pics2text.api.infrastructure.persistence.PhotoRelayUploadTaskMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Runs after login but, with lazy multipart resolution, before a multipart body is spooled. */
@Component
public class PhotoRelayAdmissionInterceptor implements HandlerInterceptor {
    private final PhotoRelayUploadTaskMapper tasks;
    private final PhotoRelayProperties properties;

    public PhotoRelayAdmissionInterceptor(PhotoRelayUploadTaskMapper tasks,
            PhotoRelayProperties properties) {
        this.tasks = tasks;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) return true;
        long length = request.getContentLengthLong();
        if (length <= 0) {
            throw new ApiException("PHOTO_RELAY_LENGTH_REQUIRED", "上传请求必须提供 Content-Length",
                    HttpStatus.LENGTH_REQUIRED);
        }
        if (tasks.countPending(properties.nodeId()) >= properties.maxPendingTasks()
                || tasks.sumPendingBytes(properties.nodeId()) + length > properties.maxStagedBytes()) {
            throw new ApiException("PHOTO_RELAY_CAPACITY_EXCEEDED", "服务端上传队列或暂存空间已满",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
        try {
            Path root = properties.tempDirectory().toAbsolutePath().normalize();
            Files.createDirectories(root);
            if (Files.getFileStore(root).getUsableSpace() < length * 2) {
                throw new ApiException("PHOTO_RELAY_DISK_PRESSURE", "服务端暂存磁盘空间不足",
                        HttpStatus.INSUFFICIENT_STORAGE);
            }
        } catch (IOException exception) {
            throw new ApiException("PHOTO_RELAY_DISK_PRESSURE", "无法使用服务端暂存磁盘",
                    HttpStatus.INSUFFICIENT_STORAGE, exception);
        }
        return true;
    }
}
