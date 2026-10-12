package com.pics2text.layout.application;

import com.pics2text.layout.domain.AutoLayoutRequest;
import com.pics2text.layout.domain.LayoutWeights;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class AutoLayoutGenerator {
    public static final String ALGORITHM_VERSION = "bucket-greedy-coverage-swap-v2";
    public static final String SCORING_VERSION = "quality-lab-crop-adjacent-v1";
    public static final int MAX_SLOTS = 250_000;
    public static final int MAX_CANDIDATES = 250_000;
    private static final int BUCKET_COUNT = 16;

    public AutoLayoutResult generate(List<LayoutTargetSlot> inputSlots,
            List<LayoutPhotoCandidate> inputCandidates, AutoLayoutRequest request) {
        if (inputSlots == null || inputSlots.isEmpty()) throw new IllegalArgumentException("文字蒙版没有可布局槽位");
        if (inputCandidates == null || inputCandidates.isEmpty()) throw new IllegalArgumentException("没有符合条件的照片");
        if (inputSlots.size() > MAX_SLOTS) throw new IllegalArgumentException("布局槽位数量超过250000上限");
        if (inputCandidates.size() > MAX_CANDIDATES) {
            throw new IllegalArgumentException("候选照片数量超过250000上限，请先缩小候选范围");
        }
        long capacity = (long) inputCandidates.size() * (request.allowPhotoReuse() ? request.maxPhotoReuse() : 1);
        if (capacity < inputSlots.size()) {
            throw new IllegalArgumentException("符合条件的照片数量不足，无法填满全部槽位");
        }

        List<LayoutTargetSlot> slots = inputSlots.stream()
                .sorted(Comparator.comparingInt(LayoutTargetSlot::slotIndex)).toList();
        List<LayoutPhotoCandidate> candidates = inputCandidates.stream()
                .sorted(Comparator.comparing(candidate -> candidate.photoId().toString())).toList();
        validateUnique(slots, candidates);
        double maxSharpness = candidates.stream().mapToDouble(LayoutPhotoCandidate::sharpness).max().orElse(1);
        double maxContrast = candidates.stream().mapToDouble(LayoutPhotoCandidate::contrast).max().orElse(1);
        List<List<Integer>> buckets = buckets(candidates);
        int[] chosen = new int[slots.size()];
        Arrays.fill(chosen, -1);
        int[] useCounts = new int[candidates.size()];
        Map<UUID, Integer> duplicateGroupCounts = new HashMap<>();
        Map<Long, Integer> positionIndex = positionIndex(slots);
        long[] evaluations = {0};

        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < slots.size(); index++) order.add(index);
        order.sort(Comparator.<Integer, Boolean>comparing(index -> !slots.get(index).edge())
                .thenComparingDouble(index -> slots.get(index).coverage())
                .thenComparingInt(index -> slots.get(index).slotIndex()));
        // When there is room for every candidate, give each photo one slot before
        // allowing reuse. Later swaps preserve these usage counts.
        boolean coverAllCandidates = slots.size() >= candidates.size();
        int assigned = 0;
        for (int slotPosition : order) {
            LayoutTargetSlot slot = slots.get(slotPosition);
            List<Integer> window = candidateWindow(slot, candidates, buckets, useCounts, request,
                    coverAllCandidates && assigned < candidates.size());
            int best = -1;
            double bestScore = Double.POSITIVE_INFINITY;
            long bestTie = Long.MAX_VALUE;
            for (int candidateIndex : window) {
                LayoutPhotoCandidate candidate = candidates.get(candidateIndex);
                double score = greedyScore(slot, candidate, slotPosition, slots, chosen, candidates,
                        positionIndex, duplicateGroupCounts, maxSharpness, maxContrast, request);
                evaluations[0]++;
                long tie = tie(request.randomSeed(), slot.slotIndex(), candidate.photoId());
                if (score < bestScore || score == bestScore && Long.compareUnsigned(tie, bestTie) < 0) {
                    best = candidateIndex;
                    bestScore = score;
                    bestTie = tie;
                }
            }
            if (best < 0) throw new IllegalArgumentException("照片容量不足，无法为槽位分配照片");
            chosen[slotPosition] = best;
            useCounts[best]++;
            UUID group = candidates.get(best).duplicateGroupId();
            if (group != null) duplicateGroupCounts.merge(group, 1, Integer::sum);
            assigned++;
        }

        double initial = averageObjective(slots, candidates, chosen, positionIndex,
                maxSharpness, maxContrast, request);
        optimize(slots, candidates, chosen, positionIndex, maxSharpness, maxContrast, request, evaluations);
        List<LayoutAssignment> assignments = assignments(slots, candidates, chosen, positionIndex,
                maxSharpness, maxContrast, request);
        double finalScore = assignments.stream().mapToDouble(LayoutAssignment::totalScore).average().orElse(0);
        int duplicateReuse = duplicateReuse(candidates, chosen);
        long estimatedMemory = estimateMemory(slots.size(), candidates.size(), request.candidateWindow());
        return new AutoLayoutResult(List.copyOf(assignments), initial, finalScore,
                average(assignments, LayoutAssignment::brightnessScore),
                average(assignments, LayoutAssignment::colorScore),
                average(assignments, LayoutAssignment::cropScore),
                average(assignments, LayoutAssignment::adjacentScore),
                (int) Arrays.stream(chosen).distinct().count(), duplicateReuse,
                evaluations[0], estimatedMemory);
    }

    private void validateUnique(List<LayoutTargetSlot> slots, List<LayoutPhotoCandidate> candidates) {
        if (slots.stream().map(LayoutTargetSlot::slotIndex).distinct().count() != slots.size()) {
            throw new IllegalArgumentException("槽位索引存在重复");
        }
        if (candidates.stream().map(LayoutPhotoCandidate::photoId).distinct().count() != candidates.size()) {
            throw new IllegalArgumentException("候选照片存在重复");
        }
    }

    private List<List<Integer>> buckets(List<LayoutPhotoCandidate> candidates) {
        List<List<Integer>> buckets = new ArrayList<>(BUCKET_COUNT);
        for (int index = 0; index < BUCKET_COUNT; index++) buckets.add(new ArrayList<>());
        for (int index = 0; index < candidates.size(); index++) {
            int bucket = Math.min(BUCKET_COUNT - 1,
                    Math.max(0, (int) (candidates.get(index).labL() / 256d * BUCKET_COUNT)));
            buckets.get(bucket).add(index);
        }
        return buckets;
    }

    private List<Integer> candidateWindow(LayoutTargetSlot slot, List<LayoutPhotoCandidate> candidates,
            List<List<Integer>> buckets, int[] useCounts, AutoLayoutRequest request, boolean unusedOnly) {
        int limit = Math.min(request.candidateWindow(), candidates.size());
        double targetL = targetL(slot, request);
        int targetBucket = Math.min(BUCKET_COUNT - 1, Math.max(0, (int) (targetL / 256d * BUCKET_COUNT)));
        LinkedHashSet<Integer> selected = new LinkedHashSet<>(limit);
        for (int radius = 0; radius < BUCKET_COUNT && selected.size() < limit; radius++) {
            addBucket(selected, buckets, targetBucket - radius, slot, useCounts, request, limit, unusedOnly);
            if (radius > 0) addBucket(selected, buckets, targetBucket + radius, slot, useCounts, request, limit, unusedOnly);
        }
        if (selected.isEmpty()) {
            for (int index = 0; index < candidates.size() && selected.size() < limit; index++) {
                if (useCounts[index] < reuseLimit(request) && (!unusedOnly || useCounts[index] == 0)) selected.add(index);
            }
        }
        return List.copyOf(selected);
    }

    private void addBucket(Set<Integer> selected, List<List<Integer>> buckets, int bucket,
            LayoutTargetSlot slot, int[] useCounts, AutoLayoutRequest request, int limit, boolean unusedOnly) {
        if (bucket < 0 || bucket >= BUCKET_COUNT || selected.size() >= limit) return;
        List<Integer> members = buckets.get(bucket);
        if (members.isEmpty()) return;
        int start = Math.floorMod((int) mix(request.randomSeed() ^ slot.slotIndex() ^ bucket), members.size());
        for (int offset = 0; offset < members.size() && selected.size() < limit; offset++) {
            int candidate = members.get((start + offset) % members.size());
            if (useCounts[candidate] < reuseLimit(request) && (!unusedOnly || useCounts[candidate] == 0)) {
                selected.add(candidate);
            }
        }
    }

    private int reuseLimit(AutoLayoutRequest request) {
        return request.allowPhotoReuse() ? request.maxPhotoReuse() : 1;
    }

    private double greedyScore(LayoutTargetSlot slot, LayoutPhotoCandidate candidate, int slotPosition,
            List<LayoutTargetSlot> slots, int[] chosen, List<LayoutPhotoCandidate> candidates,
            Map<Long, Integer> positions, Map<UUID, Integer> duplicateCounts,
            double maxSharpness, double maxContrast, AutoLayoutRequest request) {
        Components base = baseComponents(slot, candidate, maxSharpness, maxContrast, request);
        double duplicate = candidate.duplicateGroupId() != null
                && duplicateCounts.getOrDefault(candidate.duplicateGroupId(), 0) > 0 ? 1 : 0;
        double adjacent = adjacentScore(slotPosition, candidate, slots, chosen, candidates, positions);
        return weighted(base, duplicate, adjacent, request.weights());
    }

    private Components baseComponents(LayoutTargetSlot slot, LayoutPhotoCandidate candidate,
            double maxSharpness, double maxContrast, AutoLayoutRequest request) {
        double sharpnessPenalty = 1 - clamp01(candidate.sharpness() / Math.max(1e-9, maxSharpness));
        double contrastPenalty = 1 - clamp01(candidate.contrast() / Math.max(1e-9, maxContrast));
        double quality = 0.75 * sharpnessPenalty + 0.25 * contrastPenalty;
        double targetL = targetL(slot, request);
        double brightness = Math.abs(candidate.brightness() - targetL) / 255d;
        double dl = candidate.labL() - targetL;
        double da = candidate.labA() - request.targetLabA();
        double db = candidate.labB() - request.targetLabB();
        double color = Math.sqrt(dl * dl + da * da + db * db) / (Math.sqrt(3) * 255d);
        return new Components(clamp01(quality), clamp01(brightness), clamp01(color),
                clamp01(candidate.cropLoss()));
    }

    private double targetL(LayoutTargetSlot slot, AutoLayoutRequest request) {
        return Math.max(0, Math.min(255, request.targetLabL() + (1 - slot.coverage()) * 24));
    }

    private double adjacentScore(int slotPosition, LayoutPhotoCandidate candidate,
            List<LayoutTargetSlot> slots, int[] chosen, List<LayoutPhotoCandidate> candidates,
            Map<Long, Integer> positions) {
        LayoutTargetSlot slot = slots.get(slotPosition);
        int[] neighbors = neighbors(slot, positions);
        double total = 0;
        int count = 0;
        for (int neighbor : neighbors) {
            if (neighbor < 0 || chosen[neighbor] < 0) continue;
            total += similarity(candidate, candidates.get(chosen[neighbor]));
            count++;
        }
        return count == 0 ? 0 : total / count;
    }

    private double similarity(LayoutPhotoCandidate first, LayoutPhotoCandidate second) {
        long firstHash = Long.parseUnsignedLong(first.perceptualHash(), 16);
        long secondHash = Long.parseUnsignedLong(second.perceptualHash(), 16);
        double hash = 1 - Long.bitCount(firstHash ^ secondHash) / 64d;
        double dl = first.labL() - second.labL();
        double da = first.labA() - second.labA();
        double db = first.labB() - second.labB();
        double color = 1 - clamp01(Math.sqrt(dl * dl + da * da + db * db) / (Math.sqrt(3) * 255d));
        return clamp01(0.7 * hash + 0.3 * color);
    }

    private void optimize(List<LayoutTargetSlot> slots, List<LayoutPhotoCandidate> candidates,
            int[] chosen, Map<Long, Integer> positions, double maxSharpness, double maxContrast,
            AutoLayoutRequest request, long[] evaluations) {
        if (chosen.length < 2 || request.optimizationIterations() == 0) return;
        Random random = new Random(request.randomSeed());
        double currentObjective = averageObjective(slots, candidates, chosen, positions,
                maxSharpness, maxContrast, request) * slots.size();
        double bestObjective = currentObjective;
        int[] best = chosen.clone();
        for (int iteration = 0; iteration < request.optimizationIterations(); iteration++) {
            int first = random.nextInt(chosen.length);
            int second = random.nextInt(chosen.length - 1);
            if (second >= first) second++;
            if (chosen[first] == chosen[second]) continue;
            Set<Integer> affected = affected(first, second, slots, positions);
            double before = localObjective(affected, slots, candidates, chosen, positions,
                    maxSharpness, maxContrast, request);
            int temporary = chosen[first]; chosen[first] = chosen[second]; chosen[second] = temporary;
            double after = localObjective(affected, slots, candidates, chosen, positions,
                    maxSharpness, maxContrast, request);
            evaluations[0] += affected.size() * 2L;
            double progress = iteration / (double) Math.max(1, request.optimizationIterations() - 1);
            double temperature = 0.02 * (1 - progress) + 0.0001;
            boolean accepted = after <= before
                    || random.nextDouble() < Math.exp((before - after) / temperature);
            if (!accepted) {
                temporary = chosen[first]; chosen[first] = chosen[second]; chosen[second] = temporary;
            } else {
                currentObjective += after - before;
                if (currentObjective < bestObjective) {
                    bestObjective = currentObjective;
                    best = chosen.clone();
                }
            }
        }
        System.arraycopy(best, 0, chosen, 0, chosen.length);
    }

    private Set<Integer> affected(int first, int second, List<LayoutTargetSlot> slots,
            Map<Long, Integer> positions) {
        Set<Integer> affected = new TreeSet<>();
        affected.add(first); affected.add(second);
        for (int index : List.of(first, second)) {
            for (int neighbor : neighbors(slots.get(index), positions)) if (neighbor >= 0) affected.add(neighbor);
        }
        return affected;
    }

    private double localObjective(Set<Integer> affected, List<LayoutTargetSlot> slots,
            List<LayoutPhotoCandidate> candidates, int[] chosen, Map<Long, Integer> positions,
            double maxSharpness, double maxContrast, AutoLayoutRequest request) {
        return affected.stream().mapToDouble(index -> objectiveAt(index, slots, candidates, chosen,
                positions, maxSharpness, maxContrast, request)).sum();
    }

    private double averageObjective(List<LayoutTargetSlot> slots, List<LayoutPhotoCandidate> candidates,
            int[] chosen, Map<Long, Integer> positions, double maxSharpness, double maxContrast,
            AutoLayoutRequest request) {
        double total = 0;
        for (int index = 0; index < slots.size(); index++) {
            total += objectiveAt(index, slots, candidates, chosen, positions, maxSharpness, maxContrast, request);
        }
        return total / slots.size();
    }

    private double objectiveAt(int index, List<LayoutTargetSlot> slots,
            List<LayoutPhotoCandidate> candidates, int[] chosen, Map<Long, Integer> positions,
            double maxSharpness, double maxContrast, AutoLayoutRequest request) {
        LayoutPhotoCandidate candidate = candidates.get(chosen[index]);
        Components base = baseComponents(slots.get(index), candidate, maxSharpness, maxContrast, request);
        double adjacent = adjacentScore(index, candidate, slots, chosen, candidates, positions);
        return weighted(base, 0, adjacent, request.weights());
    }

    private List<LayoutAssignment> assignments(List<LayoutTargetSlot> slots,
            List<LayoutPhotoCandidate> candidates, int[] chosen, Map<Long, Integer> positions,
            double maxSharpness, double maxContrast, AutoLayoutRequest request) {
        Map<UUID, Integer> groupCounts = new HashMap<>();
        for (int candidateIndex : chosen) {
            UUID group = candidates.get(candidateIndex).duplicateGroupId();
            if (group != null) groupCounts.merge(group, 1, Integer::sum);
        }
        List<LayoutAssignment> result = new ArrayList<>(slots.size());
        for (int index = 0; index < slots.size(); index++) {
            LayoutPhotoCandidate candidate = candidates.get(chosen[index]);
            Components base = baseComponents(slots.get(index), candidate, maxSharpness, maxContrast, request);
            double duplicate = candidate.duplicateGroupId() != null
                    && groupCounts.getOrDefault(candidate.duplicateGroupId(), 0) > 1 ? 1 : 0;
            double adjacent = adjacentScore(index, candidate, slots, chosen, candidates, positions);
            result.add(new LayoutAssignment(slots.get(index).slotIndex(), candidate.photoId(),
                    weighted(base, duplicate, adjacent, request.weights()), base.quality, base.brightness,
                    base.color, base.crop, duplicate, adjacent, candidate.cropX(), candidate.cropY(),
                    candidate.cropWidth(), candidate.cropHeight()));
        }
        return result;
    }

    private double weighted(Components base, double duplicate, double adjacent, LayoutWeights weights) {
        return (base.quality * weights.quality() + base.brightness * weights.brightness()
                + base.color * weights.color() + base.crop * weights.crop()
                + duplicate * weights.duplicate() + adjacent * weights.adjacent()) / weights.sum();
    }

    private int duplicateReuse(List<LayoutPhotoCandidate> candidates, int[] chosen) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (int selected : chosen) {
            UUID group = candidates.get(selected).duplicateGroupId();
            if (group != null) counts.merge(group, 1, Integer::sum);
        }
        return counts.values().stream().mapToInt(count -> Math.max(0, count - 1)).sum();
    }

    private Map<Long, Integer> positionIndex(List<LayoutTargetSlot> slots) {
        Map<Long, Integer> positions = new HashMap<>();
        for (int index = 0; index < slots.size(); index++) {
            LayoutTargetSlot slot = slots.get(index);
            positions.put(position(slot.row(), slot.column()), index);
        }
        return positions;
    }

    private int[] neighbors(LayoutTargetSlot slot, Map<Long, Integer> positions) {
        return new int[] {
                positions.getOrDefault(position(slot.row() - 1, slot.column()), -1),
                positions.getOrDefault(position(slot.row() + 1, slot.column()), -1),
                positions.getOrDefault(position(slot.row(), slot.column() - 1), -1),
                positions.getOrDefault(position(slot.row(), slot.column() + 1), -1)
        };
    }

    private long position(int row, int column) {
        return ((long) row << 32) ^ (column & 0xffffffffL);
    }

    private long tie(long seed, int slot, UUID photoId) {
        return mix(seed ^ slot ^ photoId.getMostSignificantBits() ^ photoId.getLeastSignificantBits());
    }

    private long mix(long value) {
        value ^= value >>> 33; value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33; value *= 0xc4ceb9fe1a85ec53l;
        return value ^ value >>> 33;
    }

    private long estimateMemory(int slots, int candidates, int window) {
        return slots * 160L + candidates * 192L + Math.min(candidates, window) * 16L + 32_768;
    }

    private double average(List<LayoutAssignment> values,
            java.util.function.ToDoubleFunction<LayoutAssignment> extractor) {
        return values.stream().mapToDouble(extractor).average().orElse(0);
    }

    private double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private record Components(double quality, double brightness, double color, double crop) { }
}
