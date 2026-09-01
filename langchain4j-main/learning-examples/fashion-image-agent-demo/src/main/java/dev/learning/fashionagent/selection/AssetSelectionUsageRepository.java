package dev.learning.fashionagent.selection;

import java.time.Instant;
import java.util.Map;

interface AssetSelectionUsageRepository {

    Map<String, AssetUsage> list(AssetType assetType);

    void increment(AssetType assetType, String assetKey, String displayName, Instant selectedAt);

    record AssetUsage(long useCount, Instant lastUsedAt) {}
}
