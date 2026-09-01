-- Fashion Image Agent history schema
-- Execute with: mysql -uroot -p ai_ex_db < database/mysql-schema.sql

CREATE DATABASE IF NOT EXISTS ai_ex_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE ai_ex_db;

-- Current read model. snapshot_json lets the web layer restore the complete JobView
-- without joining every detail table for each history refresh.
CREATE TABLE IF NOT EXISTS generation_job (
    id CHAR(36) NOT NULL,
    prompt TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    current_stage VARCHAR(64) NOT NULL,
    message VARCHAR(1000) NULL,
    original_image_path VARCHAR(1000) NULL,
    clothing_image_path VARCHAR(1000) NULL,
    final_image_path VARCHAR(1000) NULL,
    reply TEXT NULL,
    error_message TEXT NULL,
    error_details LONGTEXT NULL,
    snapshot_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_generation_job_created_at (created_at),
    KEY idx_generation_job_status_updated (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Append-only audit trail. result_json stores the structured output produced by
-- prompt enhancement, image generation, visual inspection and final presentation.
CREATE TABLE IF NOT EXISTS generation_step_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    job_id CHAR(36) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    stage VARCHAR(64) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    result_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_step_event_job_id_id (job_id, id),
    KEY idx_step_event_stage_created (stage, created_at),
    CONSTRAINT fk_step_event_job
        FOREIGN KEY (job_id) REFERENCES generation_job (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS portrait_attempt (
    job_id CHAR(36) NOT NULL,
    attempt_number INT UNSIGNED NOT NULL,
    image_path VARCHAR(1000) NOT NULL,
    generation_prompt LONGTEXT NOT NULL,
    quality_report_json JSON NOT NULL,
    selected BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (job_id, attempt_number),
    KEY idx_portrait_attempt_selected (job_id, selected),
    CONSTRAINT fk_portrait_attempt_job
        FOREIGN KEY (job_id) REFERENCES generation_job (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS outfit_attempt (
    job_id CHAR(36) NOT NULL,
    attempt_number INT UNSIGNED NOT NULL,
    image_path VARCHAR(1000) NOT NULL,
    replacement_prompt LONGTEXT NOT NULL,
    quality_report_json JSON NOT NULL,
    selected BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (job_id, attempt_number),
    KEY idx_outfit_attempt_selected (job_id, selected),
    CONSTRAINT fk_outfit_attempt_job
        FOREIGN KEY (job_id) REFERENCES generation_job (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Multimodal catalog generated from the local clothing directory. The image
-- content hash is the stable id, while profile_json preserves the complete AI output.
CREATE TABLE IF NOT EXISTS clothing_profile (
    id CHAR(64) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    image_path VARCHAR(1000) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    profile_json JSON NOT NULL,
    search_text LONGTEXT NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_clothing_profile_sha256 (sha256),
    KEY idx_clothing_profile_file_name (file_name),
    KEY idx_clothing_profile_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tasks whose final quality score reaches the configured learning threshold can
-- create an approved experience. source_job_id prevents duplicate learning.
CREATE TABLE IF NOT EXISTS fashion_learned_experience (
    id CHAR(36) NOT NULL,
    source_job_id CHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    scenario TEXT NOT NULL,
    experience_json JSON NOT NULL,
    knowledge_text LONGTEXT NOT NULL,
    quality_score INT UNSIGNED NOT NULL,
    approved BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fashion_experience_source_job (source_job_id),
    KEY idx_fashion_experience_approved_created (approved, created_at),
    CONSTRAINT fk_fashion_experience_job
        FOREIGN KEY (source_job_id) REFERENCES generation_job (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Long-running video motion-transfer tasks are persisted separately from the
-- image pipeline. Only one row is actively executed at a time; two segment
-- calls belonging to that row may run concurrently.
CREATE TABLE IF NOT EXISTS video_generation_job (
                                                    id CHAR(36) NOT NULL,
    source_job_id CHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    message VARCHAR(1000) NULL,
    source_video_path VARCHAR(1000) NULL,
    final_video_path VARCHAR(1000) NULL,
    quality_score INT UNSIGNED NULL,
    quality_report_json JSON NULL,
    error_message LONGTEXT NULL,
    snapshot_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_video_generation_source (source_job_id, created_at),
    KEY idx_video_generation_status (status, updated_at),
    CONSTRAINT fk_video_generation_source_job
    FOREIGN KEY (source_job_id) REFERENCES generation_job (id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Shared fair-selection counter for local clothing and source-video assets.
-- The selector always prefers the least-used assets and uses last_used_at to
-- avoid immediately repeating the most recently selected file.
CREATE TABLE IF NOT EXISTS asset_selection_usage (
                                                     asset_type VARCHAR(20) NOT NULL,
    asset_key VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    use_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    last_used_at DATETIME(6) NOT NULL,
    PRIMARY KEY (asset_type, asset_key),
    KEY idx_asset_selection_fairness (asset_type, use_count, last_used_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Standalone text-to-video / image-to-video jobs submitted to the AutoDL ComfyUI API.
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

-- Videos downloaded for Qwen script generation. A row is created before the
-- asynchronous SnapAny download so the UI can distinguish download-only jobs.
CREATE TABLE IF NOT EXISTS qwen_video_script_job (
                                                     id CHAR(36) NOT NULL,
    source_address TEXT NOT NULL,
    source_file_name VARCHAR(255) NULL,
    video_path VARCHAR(1000) NULL,
    status VARCHAR(24) NOT NULL,
    message VARCHAR(1000) NULL,
    script_text LONGTEXT NULL,
    error_message LONGTEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_qwen_video_script_created_at (created_at),
    KEY idx_qwen_video_script_status (status, updated_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
