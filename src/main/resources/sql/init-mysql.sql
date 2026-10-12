-- 创建数据库
CREATE DATABASE IF NOT EXISTS clouddisk CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE clouddisk;

-- 创建用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    storage_used BIGINT DEFAULT 0,
    storage_limit BIGINT DEFAULT 1073741824,
    INDEX idx_username (username),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建文件元数据表
CREATE TABLE IF NOT EXISTS file_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    file_hash VARCHAR(32) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_directory BOOLEAN DEFAULT FALSE,
    parent_id BIGINT,
    version INT DEFAULT 1,
    is_compressed BOOLEAN DEFAULT FALSE,
    is_encrypted BOOLEAN DEFAULT FALSE,
    encryption_key VARCHAR(255),
    storage_path VARCHAR(500),
    owner_id BIGINT NOT NULL,
    INDEX idx_owner_id (owner_id),
    INDEX idx_file_hash (file_hash),
    INDEX idx_parent_id (parent_id),
    INDEX idx_file_path (file_path),
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_id) REFERENCES file_metadata(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建文件块表
CREATE TABLE IF NOT EXISTS file_chunks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chunk_hash VARCHAR(32) NOT NULL,
    chunk_size BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    storage_path VARCHAR(500),
    is_compressed BOOLEAN DEFAULT FALSE,
    is_encrypted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    file_id BIGINT NOT NULL,
    INDEX idx_file_id (file_id),
    INDEX idx_chunk_hash (chunk_hash),
    FOREIGN KEY (file_id) REFERENCES file_metadata(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建文件版本表
CREATE TABLE IF NOT EXISTS file_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_number INT NOT NULL,
    file_hash VARCHAR(32) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    change_description TEXT,
    is_current BOOLEAN DEFAULT FALSE,
    file_id BIGINT NOT NULL,
    INDEX idx_file_id (file_id),
    INDEX idx_version_number (version_number),
    FOREIGN KEY (file_id) REFERENCES file_metadata(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建同步事件表
CREATE TABLE IF NOT EXISTS sync_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(20) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    old_path VARCHAR(500),
    file_hash VARCHAR(32),
    file_size BIGINT,
    event_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_processed BOOLEAN DEFAULT FALSE,
    client_id VARCHAR(100),
    user_id BIGINT NOT NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_event_type (event_type),
    INDEX idx_is_processed (is_processed),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建文件共享表
CREATE TABLE IF NOT EXISTS file_shares (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    share_token VARCHAR(100) UNIQUE NOT NULL,
    permission VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    access_count INT DEFAULT 0,
    file_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    INDEX idx_share_token (share_token),
    INDEX idx_file_id (file_id),
    INDEX idx_user_id (user_id),
    FOREIGN KEY (file_id) REFERENCES file_metadata(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
