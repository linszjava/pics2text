package com.pics2text.layout.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pics2text.layout.domain.AutoLayoutRequest;
import com.pics2text.layout.domain.LayoutWeights;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutoLayoutGeneratorTest {
    private final AutoLayoutGenerator generator = new AutoLayoutGenerator();

    @Test
    void fixedSeedAndPhotoSetProduceIdenticalAssignments() {
        List<LayoutTargetSlot> slots = slots(120);
        List<LayoutPhotoCandidate> photos = photos(160);
        AutoLayoutRequest request = request(20260927L, false, 1, 48, 2_000);
        AutoLayoutResult first = generator.generate(slots, photos, request);
        List<LayoutTargetSlot> shuffledSlots = new ArrayList<>(slots);
        List<LayoutPhotoCandidate> shuffledPhotos = new ArrayList<>(photos);
        Collections.reverse(shuffledSlots);
        Collections.rotate(shuffledPhotos, 17);
        AutoLayoutResult second = generator.generate(shuffledSlots, shuffledPhotos, request);
        assertThat(second.assignments()).isEqualTo(first.assignments());
        assertThat(second.finalAverageScore()).isEqualTo(first.finalAverageScore());
        assertThat(second.candidateEvaluations()).isEqualTo(first.candidateEvaluations());
    }

    @Test
    void disallowingReuseAssignsEveryPhotoAtMostOnce() {
        AutoLayoutResult result = generator.generate(slots(100), photos(120),
                request(7, false, 1, 32, 500));
        assertThat(result.assignments()).hasSize(100);
        assertThat(result.assignments().stream().map(LayoutAssignment::photoId).distinct()).hasSize(100);
        assertThat(result.uniquePhotoCount()).isEqualTo(100);
        assertThat(result.finalAverageScore()).isLessThanOrEqualTo(result.initialAverageScore() + 1e-12);
    }

    @Test
    void reuseNeverExceedsConfiguredLimit() {
        AutoLayoutResult result = generator.generate(slots(80), photos(20),
                request(19, true, 4, 16, 300));
        assertThat(result.assignments()).hasSize(80);
        assertThat(result.assignments().stream().collect(java.util.stream.Collectors.groupingBy(
                LayoutAssignment::photoId, java.util.stream.Collectors.counting())).values())
                .allSatisfy(count -> assertThat(count).isLessThanOrEqualTo(4));
    }

    @Test
    void reuseCoversEveryCandidateBeforeFillingRemainingSlots() {
        AutoLayoutResult result = generator.generate(slots(1_938), photos(510),
                request(19, true, 10_000, 64, 1_000));
        assertThat(result.assignments()).hasSize(1_938);
        assertThat(result.uniquePhotoCount()).isEqualTo(510);
        assertThat(result.assignments().stream().map(LayoutAssignment::photoId).distinct()).hasSize(510);
    }

    @Test
    void insufficientUniquePhotosFailExplicitly() {
        assertThatThrownBy(() -> generator.generate(slots(11), photos(10),
                request(1, false, 1, 16, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("照片数量不足");
    }

    @Test
    void partitionedCandidateWindowAvoidsGiantCostMatrix() {
        List<LayoutTargetSlot> slots = slots(2_000);
        List<LayoutPhotoCandidate> photos = photos(2_200);
        AutoLayoutRequest request = request(42, false, 1, 32, 1_000);
        AutoLayoutResult result = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                Duration.ofSeconds(8), () -> generator.generate(slots, photos, request));
        long fullMatrixBytes = (long) slots.size() * photos.size() * Double.BYTES;
        assertThat(result.estimatedMemoryBytes()).isLessThan(fullMatrixBytes / 5);
        assertThat(result.candidateEvaluations()).isLessThan(250_000);
    }

    private AutoLayoutRequest request(long seed, boolean reuse, int maxReuse, int window, int iterations) {
        return new AutoLayoutRequest(seed, reuse, maxReuse, 150, 128, 128,
                window, iterations, LayoutWeights.defaults());
    }

    private List<LayoutTargetSlot> slots(int count) {
        List<LayoutTargetSlot> values = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int row = index / 50;
            int column = index % 50;
            values.add(new LayoutTargetSlot(index, row, column, column * 20, row * 20,
                    18, 18, 0.55 + index % 40 / 100d, index % 5 == 0));
        }
        return values;
    }

    private List<LayoutPhotoCandidate> photos(int count) {
        List<LayoutPhotoCandidate> values = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            UUID id = new UUID(0x1234000000000000L + index, 0x5678000000000000L + index * 31L);
            double luminance = 25 + index * 211d / Math.max(1, count - 1);
            values.add(new LayoutPhotoCandidate(id, 30 + index % 170, luminance,
                    10 + index % 70, luminance, 90 + index % 75, 95 + index * 7 % 65,
                    String.format("%016x", index * 0x9e3779b97f4a7c15L), null,
                    0.05, 0.05, 0.75 + index % 20 / 100d, 0.75 + index % 20 / 100d));
        }
        return values;
    }
}
