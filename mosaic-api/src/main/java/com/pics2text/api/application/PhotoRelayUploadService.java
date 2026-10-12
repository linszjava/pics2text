package com.pics2text.api.application;

import com.pics2text.api.common.ApiException;
import com.pics2text.api.config.PhotoRelayProperties;
import com.pics2text.api.infrastructure.persistence.PhotoAssetEntity;
import com.pics2text.api.infrastructure.persistence.PhotoAssetMapper;
import com.pics2text.api.infrastructure.persistence.PhotoRelayUploadTaskEntity;
import com.pics2text.api.infrastructure.persistence.PhotoRelayUploadTaskMapper;
import com.pics2text.api.infrastructure.persistence.ProjectEntity;
import com.pics2text.identity.domain.User;
import com.pics2text.project.domain.ProjectPermission;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.DigestInputStream;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PhotoRelayUploadService {
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final int COPY_BUFFER_SIZE = 1024 * 1024;

    private final ProjectAccessService access;
    private final PhotoRelayPersistenceService persistence;
    private final PhotoRelayUploadTaskMapper tasks;
    private final PhotoAssetMapper photos;
    private final PhotoRelayProperties properties;

    public PhotoRelayUploadService(ProjectAccessService access,
            PhotoRelayPersistenceService persistence,
            PhotoRelayUploadTaskMapper tasks,
            PhotoAssetMapper photos,
            PhotoRelayProperties properties) {
        this.access = access;
        this.persistence = persistence;
        this.tasks = tasks;
        this.photos = photos;
        this.properties = properties;
    }

    public Result receive(UUID projectId, MultipartFile file, boolean authorized) {
        User actor = access.require(projectId, ProjectPermission.UPLOAD_PHOTOS);
        ProjectEntity project = access.requireProject(projectId);
        if (!"ACTIVE".equals(project.getStatus())) {
            throw api("PROJECT_ARCHIVED", "已归档项目不能上传照片", HttpStatus.CONFLICT);
        }

        String filename = normalizeName(file.getOriginalFilename());
        String contentType = normalizeType(file.getContentType());
        long declaredSize = file.getSize();
        validateFile(project, contentType, declaredSize);
        verifyCapacity(declaredSize);

        UUID stagingId = UUID.randomUUID();
        String relativePath = projectId + "/" + stagingId + ".upload";
        Path path = resolve(relativePath);
        try {
            Files.createDirectories(path.getParent());
            verifyDiskSpace(path, declaredSize);
            StagedFile staged = copyAndHash(file, path, project.getMaxPhotoBytes());
            if (staged.byteSize() != declaredSize) {
                throw api("PHOTO_SIZE_MISMATCH", "接收字节数与声明大小不一致", HttpStatus.BAD_REQUEST);
            }

            var created = persistence.create(projectId, actor, filename, contentType,
                    staged.byteSize(), staged.sha256(), authorized, relativePath);
            if (created.duplicate()) {
                Files.deleteIfExists(path);
            } else if (tasks.queueReceived(created.task().getId(), Instant.now()) == 1) {
                created.task().setStatus("QUEUED");
            }
            return new Result(created.task(), created.photo(), created.duplicate());
        } catch (ApiException exception) {
            delete(path);
            throw exception;
        } catch (IOException | NoSuchAlgorithmException exception) {
            delete(path);
            throw api("PHOTO_RELAY_WRITE_FAILED", "服务端暂存照片失败",
                    HttpStatus.INSUFFICIENT_STORAGE);
        } catch (RuntimeException exception) {
            delete(path);
            throw exception;
        }
    }

    public Status get(UUID projectId, UUID taskId) {
        access.require(projectId, ProjectPermission.VIEW);
        PhotoRelayUploadTaskEntity task = tasks.selectById(taskId);
        if (task == null || !projectId.equals(task.getProjectId())) {
            throw api("PHOTO_RELAY_TASK_NOT_FOUND", "上传任务不存在", HttpStatus.NOT_FOUND);
        }
        return new Status(task, photos.selectById(task.getPhotoId()));
    }

    Path resolve(String relativePath) {
        Path root = properties.tempDirectory().toAbsolutePath().normalize();
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("invalid relay path");
        }
        return path;
    }

    private void validateFile(ProjectEntity project, String contentType, long byteSize) {
        if (!TYPES.contains(contentType)) {
            throw api("PHOTO_CONTENT_TYPE_UNSUPPORTED", "仅支持 JPEG、PNG 和 WebP 图片",
                    HttpStatus.BAD_REQUEST);
        }
        if (byteSize <= 0 || byteSize > project.getMaxPhotoBytes()) {
            throw api("PHOTO_SIZE_INVALID", "照片大小超出项目限制", HttpStatus.BAD_REQUEST);
        }
    }

    private void verifyCapacity(long byteSize) {
        if (tasks.countPending(properties.nodeId()) >= properties.maxPendingTasks()
                || tasks.sumPendingBytes(properties.nodeId()) + byteSize > properties.maxStagedBytes()) {
            throw api("PHOTO_RELAY_CAPACITY_EXCEEDED", "服务端上传队列或暂存空间已满",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    private void verifyDiskSpace(Path path, long byteSize) throws IOException {
        if (Files.getFileStore(path.getParent()).getUsableSpace() < byteSize * 2) {
            throw api("PHOTO_RELAY_DISK_PRESSURE", "服务端暂存磁盘空间不足",
                    HttpStatus.INSUFFICIENT_STORAGE);
        }
    }

    private StagedFile copyAndHash(MultipartFile file, Path path, long maxBytes)
            throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long copied = 0;
        try (InputStream input = file.getInputStream();
                OutputStream output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW);
                DigestInputStream hashed = new DigestInputStream(input, digest)) {
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int read;
            while ((read = hashed.read(buffer)) >= 0) {
                if (read == 0) continue;
                copied += read;
                if (copied > maxBytes) {
                    throw api("PHOTO_SIZE_INVALID", "照片大小超出项目限制", HttpStatus.BAD_REQUEST);
                }
                output.write(buffer, 0, read);
            }
        }
        return new StagedFile(copied, HexFormat.of().formatHex(digest.digest()));
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isBlank() || normalized.length() > 255
                || normalized.chars().anyMatch(c -> c == '/' || c == '\\' || Character.isISOControl(c))) {
            throw api("PHOTO_FILENAME_INVALID", "文件名无效", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeType(String type) {
        return type == null ? "" : type.toLowerCase(Locale.ROOT);
    }

    private void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) { }
    }

    private ApiException api(String code, String message, HttpStatus status) {
        return new ApiException(code, message, status);
    }

    private record StagedFile(long byteSize, String sha256) { }
    public record Result(PhotoRelayUploadTaskEntity task, PhotoAssetEntity photo, boolean duplicate) { }
    public record Status(PhotoRelayUploadTaskEntity task, PhotoAssetEntity photo) { }
}
