package dev.learning.fashionagent.selection;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAssetSelectionUsageRepository implements AssetSelectionUsageRepository {

    private final JdbcTemplate jdbcTemplate;

    JdbcAssetSelectionUsageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, AssetUsage> list(AssetType assetType) {
        Map<String, AssetUsage> result = new LinkedHashMap<>();
        jdbcTemplate.query(
                """
                SELECT asset_key, use_count, last_used_at
                FROM asset_selection_usage
                WHERE asset_type = ?
                """,
                (resultSet, rowNumber) -> Map.entry(
                        resultSet.getString("asset_key"),
                        new AssetUsage(
                                resultSet.getLong("use_count"),
                                resultSet.getTimestamp("last_used_at").toInstant())),
                assetType.name()).forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    @Override
    public void increment(
            AssetType assetType,
            String assetKey,
            String displayName,
            Instant selectedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO asset_selection_usage (
                    asset_type, asset_key, display_name, use_count, last_used_at
                ) VALUES (?, ?, ?, 1, ?)
                ON DUPLICATE KEY UPDATE
                    display_name = VALUES(display_name),
                    use_count = use_count + 1,
                    last_used_at = VALUES(last_used_at)
                """,
                assetType.name(),
                assetKey,
                displayName,
                Timestamp.from(selectedAt));
    }
}
