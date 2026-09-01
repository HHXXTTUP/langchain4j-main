package dev.learning.fashionagent.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.learning.fashionagent.selection.AssetSelectionUsageRepository.AssetUsage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BalancedAssetSelectionServiceTest {

    @Test
    void shouldUseEveryAssetBeforeStartingTheNextCycle() {
        InMemoryUsageRepository repository = new InMemoryUsageRepository();
        BalancedAssetSelectionService service = new BalancedAssetSelectionService(repository);
        List<BalancedAssetSelectionService.Candidate> candidates = List.of(
                new BalancedAssetSelectionService.Candidate(Path.of("a.png"), 0.9),
                new BalancedAssetSelectionService.Candidate(Path.of("b.png"), 0.6),
                new BalancedAssetSelectionService.Candidate(Path.of("c.png"), 0.3));

        var first = service.select(AssetType.CLOTHING, candidates);
        var second = service.select(AssetType.CLOTHING, candidates);
        var third = service.select(AssetType.CLOTHING, candidates);
        var fourth = service.select(AssetType.CLOTHING, candidates);

        assertEquals("a.png", first.assetKey());
        assertEquals("b.png", second.assetKey());
        assertEquals("c.png", third.assetKey());
        assertEquals("a.png", fourth.assetKey());
        assertNotEquals(third.assetKey(), fourth.assetKey());
        assertTrue(fourth.avoidedImmediateRepeat());
    }

    @Test
    void shouldKeepUsageDifferenceAtMostOneAcrossManySelections() {
        InMemoryUsageRepository repository = new InMemoryUsageRepository();
        BalancedAssetSelectionService service = new BalancedAssetSelectionService(repository);
        List<BalancedAssetSelectionService.Candidate> candidates = List.of(
                BalancedAssetSelectionService.Candidate.of(Path.of("dance-1.mp4")),
                BalancedAssetSelectionService.Candidate.of(Path.of("dance-2.mp4")),
                BalancedAssetSelectionService.Candidate.of(Path.of("dance-3.mp4")),
                BalancedAssetSelectionService.Candidate.of(Path.of("dance-4.mp4")));

        String previousKey = null;
        for (int index = 0; index < 41; index++) {
            String selectedKey = service.select(AssetType.VIDEO, candidates).assetKey();
            assertNotEquals(previousKey, selectedKey);
            previousKey = selectedKey;
        }

        List<Long> counts = repository.list(AssetType.VIDEO).values().stream()
                .map(AssetUsage::useCount)
                .sorted()
                .toList();
        assertEquals(4, counts.size());
        assertTrue(counts.get(counts.size() - 1) - counts.get(0) <= 1);
    }

    @Test
    void shouldContinueFromPersistedUsageAfterRestart() {
        InMemoryUsageRepository repository = new InMemoryUsageRepository();
        repository.usages(AssetType.VIDEO).put("old.mp4", new AssetUsage(5, Instant.now()));
        repository.usages(AssetType.VIDEO).put("fresh.mp4", new AssetUsage(1, Instant.now().minusSeconds(60)));
        BalancedAssetSelectionService restartedService = new BalancedAssetSelectionService(repository);

        var selected = restartedService.select(AssetType.VIDEO, List.of(
                BalancedAssetSelectionService.Candidate.of(Path.of("old.mp4")),
                BalancedAssetSelectionService.Candidate.of(Path.of("fresh.mp4"))));

        assertEquals("fresh.mp4", selected.assetKey());
        assertEquals(1, selected.useCountBefore());
        assertEquals(2, selected.useCountAfter());
    }

    @Test
    void shouldTreatSameVideoFileNameInDifferentFoldersAsDifferentAssets() {
        InMemoryUsageRepository repository = new InMemoryUsageRepository();
        BalancedAssetSelectionService service = new BalancedAssetSelectionService(repository);
        List<BalancedAssetSelectionService.Candidate> candidates = List.of(
                BalancedAssetSelectionService.Candidate.of(Path.of("202607", "video-001.mp4"), "202607/video-001.mp4"),
                BalancedAssetSelectionService.Candidate.of(Path.of("202608", "video-001.mp4"), "202608/video-001.mp4"));

        var first = service.select(AssetType.VIDEO, candidates);
        var second = service.select(AssetType.VIDEO, candidates);

        assertEquals(2, first.candidateCount());
        assertNotEquals(first.assetKey(), second.assetKey());
        assertEquals(2, repository.list(AssetType.VIDEO).size());
    }

    private static final class InMemoryUsageRepository implements AssetSelectionUsageRepository {

        private final Map<AssetType, Map<String, AssetUsage>> usages = new HashMap<>();

        @Override
        public Map<String, AssetUsage> list(AssetType assetType) {
            return Map.copyOf(usages(assetType));
        }

        @Override
        public void increment(AssetType assetType, String assetKey, String displayName, Instant selectedAt) {
            usages(assetType).compute(assetKey, (ignored, previous) -> new AssetUsage(
                    previous == null ? 1 : previous.useCount() + 1,
                    selectedAt));
        }

        private Map<String, AssetUsage> usages(AssetType assetType) {
            return usages.computeIfAbsent(assetType, ignored -> new HashMap<>());
        }
    }
}
