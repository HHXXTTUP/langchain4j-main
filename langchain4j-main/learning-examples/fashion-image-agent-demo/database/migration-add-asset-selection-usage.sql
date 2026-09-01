USE ai_ex_db;

-- Persists fair local-asset rotation across application restarts.
CREATE TABLE IF NOT EXISTS asset_selection_usage (
    asset_type VARCHAR(20) NOT NULL,
    asset_key VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    use_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    last_used_at DATETIME(6) NOT NULL,
    PRIMARY KEY (asset_type, asset_key),
    KEY idx_asset_selection_fairness (asset_type, use_count, last_used_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
