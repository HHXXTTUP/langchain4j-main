USE ai_ex_db;

CREATE TABLE IF NOT EXISTS comfyui_video_generation_job (
    id CHAR(36) NOT NULL,
    prompt TEXT NOT NULL,
    duration_seconds INT UNSIGNED NOT NULL,
    resolution VARCHAR(32) NOT NULL,
    image_count INT UNSIGNED NOT NULL,
    status VARCHAR(24) NOT NULL,
    message VARCHAR(1000) NULL,
    remote_task_id VARCHAR(128) NULL,
    remote_result_url TEXT NULL,
    final_video_path VARCHAR(1000) NULL,
    error_message LONGTEXT NULL,
    snapshot_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_comfyui_video_created_at (created_at),
    KEY idx_comfyui_video_status (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
