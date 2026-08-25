CREATE TABLE IF NOT EXISTS tb_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    original_filename VARCHAR(512) NOT NULL COMMENT '原始文件名',
    stored_filename VARCHAR(255) NOT NULL COMMENT 'OSS存储文件名',
    file_size BIGINT NOT NULL COMMENT '文件大小(字节)',
    content_type VARCHAR(128) COMMENT '文件MIME类型',
    file_hash VARCHAR(64) NOT NULL COMMENT 'SHA-256哈希值',
    oss_url VARCHAR(1024) NOT NULL COMMENT 'OSS访问URL',
    upload_time DATETIME NOT NULL COMMENT '上传时间',
    INDEX idx_file_hash (file_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件上传记录表';