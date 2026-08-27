CREATE TABLE IF NOT EXISTS tb_share (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    share_code      VARCHAR(12)  NOT NULL COMMENT '分享短码',
    file_id         BIGINT       NOT NULL COMMENT '关联文件ID',
    user_id         BIGINT       NOT NULL COMMENT '分享者ID',
    password        VARCHAR(256) DEFAULT NULL COMMENT '提取码, BCrypt哈希, NULL=无需提取码',
    expire_time     DATETIME     NOT NULL COMMENT '过期时间',
    max_downloads   INT          DEFAULT -1  COMMENT '最大下载次数, -1=不限',
    download_count  INT          DEFAULT 0   COMMENT '已下载次数',
    status          TINYINT      DEFAULT 1   COMMENT '1=有效, 0=失效',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_share_code (share_code),
    INDEX idx_user_id (user_id),
    INDEX idx_file_id (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件分享表';