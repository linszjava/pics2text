package com.pics2text.api.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pics2text.api.infrastructure.persistence.PhotoAssetEntity;
import com.pics2text.api.infrastructure.persistence.PhotoAssetMapper;
import com.pics2text.api.infrastructure.persistence.PhotoRelayUploadTaskEntity;
import com.pics2text.api.infrastructure.persistence.PhotoRelayUploadTaskMapper;
import com.pics2text.api.infrastructure.storage.ObjectStorageGateway;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PhotoRelayUploadWorkerTest {
    @TempDir
    Path temp;

    @Test
    void neverRunsMoreThanConfiguredConcurrencyAndCleansSuccessfulFiles() throws Exception {
        Fixture fixture = new Fixture(4, 8);
        CountDownLatch entered = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);
        List<PhotoRelayUploadTaskEntity> batch = new ArrayList<>();
        for (int index = 0; index < 4; index++) batch.add(fixture.task("f" + index, 3, 0));
        when(fixture.persistence.claimBatch(anyInt())).thenReturn(batch);
        doAnswer(invocation -> {
            entered.countDown();
            release.await(3, TimeUnit.SECONDS);
            return null;
        }).when(fixture.storage).putFile(any(), any(), anyLong(), any());

        fixture.worker.dispatch();

        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(fixture.worker.activeUploads()).isEqualTo(4);
        release.countDown();
        verify(fixture.tasks, org.mockito.Mockito.timeout(3000).times(4)).succeed(any(), any(), any());
        for (PhotoRelayUploadTaskEntity task : batch) {
            assertThat(fixture.service.resolve(task.getRelativePath())).doesNotExist();
        }
        fixture.worker.close();
    }

    @Test
    void saturatedExecutorStopsClaimingMoreDurableTasks() throws Exception {
        Fixture fixture = new Fixture(1, 1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var first = fixture.task("one", 3, 0);
        var second = fixture.task("two", 3, 0);
        var third = fixture.task("three", 3, 0);
        when(fixture.persistence.claimBatch(anyInt()))
                .thenReturn(List.of(first), List.of(second), List.of(third));
        doAnswer(invocation -> {
            entered.countDown();
            release.await(3, TimeUnit.SECONDS);
            return null;
        }).when(fixture.storage).putFile(any(), any(), anyLong(), any());

        fixture.worker.dispatch();
        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
        fixture.worker.dispatch();
        fixture.worker.dispatch();

        verify(fixture.persistence, times(2)).claimBatch(anyInt());
        release.countDown();
        fixture.worker.close();
    }

    @Test
    void terminalExceptionReleasesCapacityAndDeletesTemp() throws Exception {
        Fixture fixture = new Fixture(1, 1);
        var task = fixture.task("failed", 1, 0);
        when(fixture.persistence.claimBatch(anyInt())).thenReturn(List.of(task));
        doThrow(new RuntimeException("storage down"))
                .when(fixture.storage).putFile(any(), any(), anyLong(), any());

        fixture.worker.dispatch();

        verify(fixture.tasks, org.mockito.Mockito.timeout(2000)).failAttempt(
                eq(task.getId()), eq(task.getClaimToken()), eq("FAILED"), any(), any(), any(), any());
        assertThat(fixture.service.resolve(task.getRelativePath())).doesNotExist();
        assertThat(fixture.worker.activeUploads()).isZero();
        fixture.worker.close();
    }

    @Test
    void recoversReceivedAndStaleClaimsBeforeDispatch() {
        Fixture fixture = new Fixture(1, 1);
        when(fixture.persistence.claimBatch(anyInt())).thenReturn(List.of());

        fixture.worker.dispatch();

        verify(fixture.tasks).recoverReceived(eq("node-a"), any());
        verify(fixture.tasks).recoverStale(eq("node-a"), any(), any());
        fixture.worker.close();
    }

    @Test
    void staleExecutorCannotUploadCompleteOrDeleteAfterLosingClaim() throws Exception {
        Fixture fixture = new Fixture(1, 1);
        var task = fixture.task("stale", 1, 0);
        when(fixture.persistence.claimBatch(anyInt())).thenReturn(List.of(task));
        when(fixture.tasks.heartbeat(eq(task.getId()), eq(task.getClaimToken()), any())).thenReturn(0);

        fixture.worker.dispatch();

        verify(fixture.tasks, org.mockito.Mockito.timeout(1000))
                .heartbeat(eq(task.getId()), eq(task.getClaimToken()), any());
        verifyNoInteractions(fixture.storage, fixture.completion);
        assertThat(fixture.service.resolve(task.getRelativePath())).exists();
        fixture.worker.close();
    }

    final class Fixture {
        final PhotoRelayPersistenceService persistence = mock(PhotoRelayPersistenceService.class);
        final PhotoRelayUploadTaskMapper tasks = mock(PhotoRelayUploadTaskMapper.class);
        final PhotoAssetMapper photos = mock(PhotoAssetMapper.class);
        final ObjectStorageGateway storage = mock(ObjectStorageGateway.class);
        final PhotoAssetService completion = mock(PhotoAssetService.class);
        final PhotoRelayUploadService service = mock(PhotoRelayUploadService.class);
        final PhotoRelayUploadWorker worker;

        Fixture(int concurrency, int queueCapacity) {
            var properties = PhotoRelayPersistenceServiceTest.properties(concurrency, queueCapacity);
            when(service.resolve(anyString())).thenAnswer(invocation ->
                    temp.resolve(invocation.getArgument(0, String.class)));
            when(tasks.heartbeat(any(), any(), any())).thenReturn(1);
            when(tasks.failAttempt(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
            worker = new PhotoRelayUploadWorker(
                    persistence, tasks, photos, storage, completion, service, properties);
        }

        PhotoRelayUploadTaskEntity task(String name, int maxAttempts, int attempts) throws Exception {
            Path path = temp.resolve(name);
            Files.write(path, new byte[]{1});
            PhotoRelayUploadTaskEntity task = new PhotoRelayUploadTaskEntity();
            task.setId(UUID.randomUUID());
            task.setPhotoId(UUID.randomUUID());
            task.setUploaderId(UUID.randomUUID());
            task.setRelativePath(name);
            task.setByteSize(1L);
            task.setMaxAttempts(maxAttempts);
            task.setAttemptCount(attempts);
            task.setClaimToken(UUID.randomUUID());
            PhotoAssetEntity photo = new PhotoAssetEntity();
            photo.setId(task.getPhotoId());
            photo.setOriginalKey("key/" + name);
            photo.setDeclaredContentType("image/jpeg");
            photo.setByteSize(1L);
            when(photos.selectById(task.getPhotoId())).thenReturn(photo);
            when(tasks.succeed(eq(task.getId()), any(), any())).thenReturn(1);
            return task;
        }
    }
}
