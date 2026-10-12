package com.pics2text.api.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pics2text.api.config.ObjectStorageProperties;
import com.pics2text.api.config.PhotoRelayProperties;
import com.pics2text.api.infrastructure.persistence.PhotoAssetMapper;
import com.pics2text.api.infrastructure.persistence.PhotoRelayUploadTaskEntity;
import com.pics2text.api.infrastructure.persistence.PhotoRelayUploadTaskMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhotoRelayPersistenceServiceTest {
    @Test
    void returnsOnlyRowsWhoseAtomicClaimSucceeded() {
        PhotoRelayUploadTaskMapper mapper = mock(PhotoRelayUploadTaskMapper.class);
        PhotoRelayUploadTaskEntity first = task();
        PhotoRelayUploadTaskEntity second = task();
        when(mapper.findClaimCandidates(eq("node-a"), any(), eq(2))).thenReturn(List.of(first, second));
        when(mapper.claim(eq(first.getId()), any(), any())).thenReturn(1);
        when(mapper.claim(eq(second.getId()), any(), any())).thenReturn(0);
        PhotoRelayPersistenceService service = new PhotoRelayPersistenceService(
                mock(PhotoAssetMapper.class),
                mapper,
                mock(ObjectKeyPolicy.class),
                mock(ObjectStorageProperties.class),
                properties(4, 16),
                mock(AuditService.class));

        List<PhotoRelayUploadTaskEntity> claimed = service.claimBatch(2);

        assertThat(claimed).containsExactly(first);
        assertThat(first.getClaimToken()).isNotNull();
        assertThat(second.getClaimToken()).isNotNull().isNotEqualTo(first.getClaimToken());
    }

    private static PhotoRelayUploadTaskEntity task() {
        PhotoRelayUploadTaskEntity task = new PhotoRelayUploadTaskEntity();
        task.setId(UUID.randomUUID());
        return task;
    }

    static PhotoRelayProperties properties(int concurrency, int queueCapacity) {
        return new PhotoRelayProperties(
                Path.of("target/relay-test"),
                "node-a",
                concurrency,
                queueCapacity,
                20,
                1_000_000,
                3,
                Duration.ofMinutes(1),
                Duration.ofMillis(1));
    }
}
