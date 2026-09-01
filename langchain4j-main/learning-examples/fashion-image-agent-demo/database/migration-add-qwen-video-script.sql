-- Run once for existing installations (mysql -uroot -p ai_ex_db < this file)
USE ai_ex_db;

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
