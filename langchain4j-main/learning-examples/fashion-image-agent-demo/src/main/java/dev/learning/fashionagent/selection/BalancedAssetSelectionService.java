package dev.learning.fashionagent.selection;

import dev.learning.fashionagent.selection.AssetSelectionUsageRepository.AssetUsage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BalancedAssetSelectionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BalancedAssetSelectionService.class);

    private final AssetSelectionUsageRepository repository;
    private final Map<AssetType, Map<String, AssetUsage>> memoryUsage = new EnumMap<>(AssetType.class);
    private final Map<AssetType, String> lastSelectedKeys = new EnumMap<>(AssetType.class);
    private final AtomicBoolean databaseWarningLogged = new AtomicBoolean();

    public BalancedAssetSelectionService(AssetSelectionUsageRepository repository) {
        this.repository = repository;
        for (AssetType type : AssetType.values()) {
            memoryUsage.put(type, new HashMap<>());
        }
    }

    public synchronized Selection select(AssetType assetType, List<Candidate> requestedCandidates) {
        if (requestedCandidates == null || requestedCandidates.isEmpty()) {
            throw new IllegalArgumentException("均衡选择的候选素材不能为空");
        }
        Map<String, Candidate> candidatesByKey = new LinkedHashMap<>();
        for (Candidate requested : requestedCandidates) {
            Path path = requested.path().toAbsolutePath().normalize();
            Candidate candidate = new Candidate(path, normalizedRelevance(requested.relevance()), requested.assetKey());
            candidatesByKey.putIfAbsent(keyOf(candidate), candidate);
        }
        if (candidatesByKey.isEmpty()) {
            throw new IllegalArgumentException("均衡选择的候选素材不能为空");
        }

        Map<String, AssetUsage> usage = new HashMap<>(memoryUsage.get(assetType));
        try {
            repository.list(assetType).forEach((key, value) -> usage.merge(key, value, BalancedAssetSelectionService::newer));
            databaseWarningLogged.set(false);
        } catch (RuntimeException exception) {
            logDatabaseFallback(exception);
        }

        long minimumUseCount = candidatesByKey.keySet().stream()
                .map(key -> usage.getOrDefault(key, unused()).useCount())
                .min(Long::compareTo)
                .orElse(0L);
        List<Candidate> leastUsed = candidatesByKey.entrySet().stream()
                .filter(entry -> usage.getOrDefault(entry.getKey(), unused()).useCount() == minimumUseCount)
                .map(Map.Entry::getValue)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        String mostRecentlyUsedKey = lastSelectedKeys.get(assetType);
        if (mostRecentlyUsedKey == null) {
            mostRecentlyUsedKey = mostRecentlyUsedKey(candidatesByKey.keySet(), usage);
        }
        String lastSelectedKey = mostRecentlyUsedKey;
        boolean avoidedImmediateRepeat = leastUsed.size() > 1
                && lastSelectedKey != null
                && leastUsed.removeIf(candidate -> keyOf(candidate).equals(lastSelectedKey));
        Candidate selected = leastUsed.stream()
                .sorted(Comparator.comparingDouble(Candidate::relevance).reversed()
                        .thenComparing(BalancedAssetSelectionService::keyOf))
                .findFirst()
                .orElseThrow();

        String selectedKey = keyOf(selected);
        AssetUsage previous = usage.getOrDefault(selectedKey, unused());
        Instant selectedAt = Instant.now();
        AssetUsage updated = new AssetUsage(previous.useCount() + 1, selectedAt);
        memoryUsage.get(assetType).put(selectedKey, updated);
        lastSelectedKeys.put(assetType, selectedKey);
        try {
            repository.increment(
                    assetType,
                    selectedKey,
                    selected.path().getFileName().toString(),
                    selectedAt);
            databaseWarningLogged.set(false);
        } catch (RuntimeException exception) {
            logDatabaseFallback(exception);
        }

        LOGGER.info(
                "素材均衡选择 type={} selected={} useCountBefore={} useCountAfter={} candidateCount={} avoidedImmediateRepeat={}",
                assetType,
                selected.path().getFileName(),
                previous.useCount(),
                updated.useCount(),
                candidatesByKey.size(),
                avoidedImmediateRepeat);
        return new Selection(
                selected.path(),
                selectedKey,
                previous.useCount(),
                updated.useCount(),
                candidatesByKey.size(),
                avoidedImmediateRepeat);
    }

    private void logDatabaseFallback(RuntimeException exception) {
        if (databaseWarningLogged.compareAndSet(false, true)) {
            LOGGER.warn(
                    "素材使用次数表不可用，本次运行改用内存均衡计数；请检查本地 H2 data 目录和 schema-h2.sql：{}",
                    exception.getMessage());
        }
    }

    private static AssetUsage newer(AssetUsage first, AssetUsage second) {
        if (second.useCount() > first.useCount()) {
            return second;
        }
        if (second.useCount() < first.useCount()) {
            return first;
        }
        if (first.lastUsedAt() == null) {
            return second;
        }
        if (second.lastUsedAt() == null) {
            return first;
        }
        return second.lastUsedAt().isAfter(first.lastUsedAt()) ? second : first;
    }

    private static String mostRecentlyUsedKey(Iterable<String> candidateKeys, Map<String, AssetUsage> usage) {
        String selectedKey = null;
        Instant selectedAt = null;
        for (String key : candidateKeys) {
            Instant lastUsedAt = usage.getOrDefault(key, unused()).lastUsedAt();
            if (lastUsedAt != null && (selectedAt == null || lastUsedAt.isAfter(selectedAt))) {
                selectedKey = key;
                selectedAt = lastUsedAt;
            }
        }
        return selectedKey;
    }

    private static String keyOf(Candidate candidate) {
        if (candidate.assetKey() != null && !candidate.assetKey().isBlank()) {
            return candidate.assetKey().trim().toLowerCase(Locale.ROOT);
        }
        return candidate.path().getFileName().toString().toLowerCase(Locale.ROOT);
    }

    private static double normalizedRelevance(double relevance) {
        return Double.isFinite(relevance) ? relevance : 0;
    }

    private static AssetUsage unused() {
        return new AssetUsage(0, null);
    }

    public record Candidate(Path path, double relevance, String assetKey) {

        public Candidate(Path path, double relevance) {
            this(path, relevance, null);
        }

        public static Candidate of(Path path) {
            return new Candidate(path, 0);
        }

        public static Candidate of(Path path, String assetKey) {
            return new Candidate(path, 0, assetKey);
        }
    }

    public record Selection(
            Path path,
            String assetKey,
            long useCountBefore,
            long useCountAfter,
            int candidateCount,
            boolean avoidedImmediateRepeat) {}
}
