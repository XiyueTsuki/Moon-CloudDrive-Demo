DROP TABLE IF EXISTS tb_share;
CREATE TABLE tb_share (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    share_code      VARCHAR(255)  NOT NULL,
    file_id         BIGINT        NOT NULL,
    user_id         BIGINT        NOT NULL,
    password        VARCHAR(255)  DEFAULT NULL,
    expire_time     TIMESTAMP     NOT NULL,
    max_downloads   INT           DEFAULT -1,
    download_count  INT           DEFAULT 0,
    status          INT           DEFAULT 1,
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);