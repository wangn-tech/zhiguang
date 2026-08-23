CREATE TABLE users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    phone VARCHAR(32) NULL,
    email VARCHAR(128) NULL,
    password_hash VARCHAR(128) NULL,
    nickname VARCHAR(64) NOT NULL,
    avatar TEXT NULL,
    bio VARCHAR(512) NULL,
    zg_id VARCHAR(64) NULL,
    gender VARCHAR(16) NULL,
    birthday DATE NULL,
    school VARCHAR(128) NULL,
    tags_json JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_phone (phone),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_zg_id (zg_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE login_logs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NULL,
    identifier VARCHAR(128) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    ip VARCHAR(45) NULL,
    user_agent VARCHAR(512) NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY ix_login_logs_user_created_at (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
