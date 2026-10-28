-- =====================================================================
-- LegacyGuard 数据库结构 v1.0
-- MySQL 8.0.16+（使用了 CHECK 约束与生成列）
-- 执行：mysql -u root -p < sql/schema.sql
--
-- 设计说明见 docs/03-数据库设计.md
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS legacy_guard
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE legacy_guard;

-- ---------------------------------------------------------------------
-- 1. 用户表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username            VARCHAR(50)     NOT NULL                COMMENT '登录名，软删后可复用',
    password_hash       VARCHAR(100)    NOT NULL                COMMENT 'BCrypt 哈希，绝不明文',
    display_name        VARCHAR(50)     NOT NULL                COMMENT '显示名',
    email               VARCHAR(120)    NULL                    COMMENT '邮箱',
    phone               VARCHAR(20)     NULL                    COMMENT '手机号',
    role                VARCHAR(16)     NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
    status              TINYINT         NOT NULL DEFAULT 0      COMMENT '0-正常 1-锁定 2-禁用',
    failed_login_count  INT             NOT NULL DEFAULT 0      COMMENT '连续登录失败次数',
    locked_until        DATETIME        NULL                    COMMENT '锁定截止时间',
    last_login_at       DATETIME        NULL                    COMMENT '最近登录时间',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at          DATETIME        NULL                    COMMENT '软删除标记',
    -- 有效用户名唯一：已注销的用户不再占用用户名
    username_active     VARCHAR(50)     GENERATED ALWAYS AS (IF(deleted_at IS NULL, username, NULL)) STORED,
    email_active        VARCHAR(120)    GENERATED ALWAYS AS (IF(deleted_at IS NULL, email, NULL)) STORED,
    phone_active        VARCHAR(20)     GENERATED ALWAYS AS (IF(deleted_at IS NULL, phone, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username_active (username_active),
    UNIQUE KEY uk_email_active (email_active),
    UNIQUE KEY uk_phone_active (phone_active),
    KEY idx_role_status (role, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户表';

-- ---------------------------------------------------------------------
-- 2. 数字资产（仅元数据，密文在 digital_asset_secret）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS digital_asset;
CREATE TABLE digital_asset (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    owner_id        BIGINT UNSIGNED NOT NULL                COMMENT '所有者',
    name            VARCHAR(100)    NOT NULL                COMMENT '资产名称',
    asset_type      VARCHAR(32)     NOT NULL                COMMENT 'DOCUMENT/IMAGE/NOTE/ACCOUNT_RECOVERY/OTHER',
    description     VARCHAR(500)    NULL                    COMMENT '普通描述，不含敏感内容',
    file_name       VARCHAR(255)    NULL                    COMMENT '文件元数据：文件名',
    file_size       BIGINT UNSIGNED NULL                    COMMENT '文件元数据：字节数',
    mime_type       VARCHAR(100)    NULL                    COMMENT '文件元数据：MIME',
    status          TINYINT         NOT NULL DEFAULT 0      COMMENT '0-正常 1-归档 2-已删除',
    version         INT             NOT NULL DEFAULT 0      COMMENT '乐观锁',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      DATETIME        NULL                    COMMENT '软删除标记',
    PRIMARY KEY (id),
    KEY idx_owner_status (owner_id, status),
    KEY idx_owner_updated (owner_id, updated_at),
    CONSTRAINT fk_asset_owner FOREIGN KEY (owner_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='数字资产元数据';

-- ---------------------------------------------------------------------
-- 3. 资产密文（与元数据隔离，1:1）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS digital_asset_secret;
CREATE TABLE digital_asset_secret (
    asset_id        BIGINT UNSIGNED NOT NULL                COMMENT '与 digital_asset 一对一',
    wrapped_dek     VARCHAR(128)    NOT NULL                COMMENT '被 KEK 包裹的数据密钥（Base64），信封加密核心',
    kek_version     INT             NOT NULL DEFAULT 1      COMMENT '主密钥版本，支持轮换',
    cipher_text     MEDIUMTEXT      NOT NULL                COMMENT 'AES-256-GCM 密文（Base64）',
    iv              VARCHAR(32)     NOT NULL                COMMENT 'GCM 随机 12 字节 nonce（Base64）',
    auth_tag        VARCHAR(32)     NOT NULL                COMMENT 'GCM 认证标签（Base64）',
    content_hash    CHAR(64)        NOT NULL                COMMENT '明文 SHA-256，用于完整性校验',
    plain_size      INT UNSIGNED    NOT NULL                COMMENT '明文字节数',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (asset_id),
    CONSTRAINT fk_secret_asset FOREIGN KEY (asset_id) REFERENCES digital_asset (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='资产密文';

-- ---------------------------------------------------------------------
-- 4. 可信联系人关系
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS trusted_contact;
CREATE TABLE trusted_contact (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    owner_id        BIGINT UNSIGNED NOT NULL                COMMENT '资产所有者',
    contact_user_id BIGINT UNSIGNED NOT NULL                COMMENT '联系人（平台用户）',
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACTIVE/REJECTED/REVOKED',
    note            VARCHAR(255)    NULL                    COMMENT '所有者写的备注',
    version         INT             NOT NULL DEFAULT 0      COMMENT '乐观锁',
    invited_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at    DATETIME        NULL,
    revoked_at      DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- 同一对用户只能存在一条有效关系；失效记录为 NULL 不参与唯一
    active_key      VARCHAR(64)     GENERATED ALWAYS AS (
                        IF(status IN ('PENDING', 'ACTIVE'), CONCAT(owner_id, ':', contact_user_id), NULL)
                    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_active_pair (active_key),
    KEY idx_owner_status (owner_id, status),
    KEY idx_contact_status (contact_user_id, status),
    CONSTRAINT fk_tc_owner FOREIGN KEY (owner_id) REFERENCES sys_user (id),
    CONSTRAINT fk_tc_contact FOREIGN KEY (contact_user_id) REFERENCES sys_user (id),
    CONSTRAINT ck_tc_not_self CHECK (owner_id <> contact_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='可信联系人关系';

-- ---------------------------------------------------------------------
-- 5. 状态确认配置（每用户一条，心跳状态机载体）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS heartbeat_confirm_config;
CREATE TABLE heartbeat_confirm_config (
    user_id                 BIGINT UNSIGNED NOT NULL COMMENT '主键即用户 ID',
    cycle_days              INT             NOT NULL DEFAULT 90  COMMENT '确认周期（天）',
    reminder_rounds         TINYINT         NOT NULL DEFAULT 3   COMMENT '最大提醒轮数',
    reminder_interval_hours INT             NOT NULL DEFAULT 72  COMMENT '提醒间隔（小时）',
    status                  VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE'
                            COMMENT 'ACTIVE/REMINDING/VERIFYING/REVIEWING/SUSPENDED',
    cycle_no                INT             NOT NULL DEFAULT 1   COMMENT '当前周期号，定时任务幂等键',
    last_confirm_at         DATETIME        NOT NULL             COMMENT '最近确认时间',
    next_deadline_at        DATETIME        NOT NULL             COMMENT '下次确认截止',
    reminder_count          INT             NOT NULL DEFAULT 0   COMMENT '本周期已发提醒轮数',
    last_remind_at          DATETIME        NULL                 COMMENT '最近提醒时间',
    suspended_from_status   VARCHAR(16)     NULL                 COMMENT '冻结前状态，解冻用',
    version                 INT             NOT NULL DEFAULT 0   COMMENT '乐观锁',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    KEY idx_status_deadline (status, next_deadline_at),
    KEY idx_status_remind (status, last_remind_at),
    CONSTRAINT fk_hb_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='状态确认配置';

-- ---------------------------------------------------------------------
-- 6. 状态确认流水
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS heartbeat_record;
CREATE TABLE heartbeat_record (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NOT NULL,
    cycle_no            INT             NOT NULL COMMENT '被完成的周期号',
    confirm_type        VARCHAR(16)     NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/AUTO',
    prev_deadline_at    DATETIME        NOT NULL COMMENT '确认前的截止时间',
    next_deadline_at    DATETIME        NOT NULL COMMENT '确认后的新截止时间',
    source_ip           VARCHAR(45)     NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 同一周期只能确认一次，防止重复提交
    UNIQUE KEY uk_user_cycle (user_id, cycle_no),
    KEY idx_user_created (user_id, created_at),
    CONSTRAINT fk_hbrec_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='状态确认流水';

-- ---------------------------------------------------------------------
-- 7. 授权案件
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS authorization_case;
CREATE TABLE authorization_case (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    case_no             VARCHAR(32)     NOT NULL COMMENT '业务编号，对外暴露，避免泄露自增值',
    owner_id            BIGINT UNSIGNED NOT NULL,
    contact_id          BIGINT UNSIGNED NOT NULL COMMENT 'trusted_contact.id',
    trigger_type        VARCHAR(24)     NOT NULL DEFAULT 'HEARTBEAT_MISS' COMMENT 'HEARTBEAT_MISS/MANUAL',
    status              VARCHAR(24)     NOT NULL DEFAULT 'DRAFT'
                        COMMENT 'DRAFT/PENDING_REVIEW/REVIEWING/WAITING_PERIOD/AUTHORIZED/REJECTED/CANCELLED/DISPUTED/REVOKED',
    reason              VARCHAR(500)    NULL COMMENT '申请人填写的理由',
    review_comment      VARCHAR(500)    NULL COMMENT '最近一次审批意见',
    wait_period_end_at  DATETIME        NULL COMMENT '等待期截止',
    authorized_at       DATETIME        NULL,
    revoked_at          DATETIME        NULL,
    closed_at           DATETIME        NULL COMMENT '进入终态的时间',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁，审批并发控制',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- 同一 owner + 联系人只能有一个未终结案件
    active_key          VARCHAR(64)     GENERATED ALWAYS AS (
                            IF(status IN ('REJECTED', 'CANCELLED', 'REVOKED'), NULL,
                               CONCAT(owner_id, ':', contact_id))
                        ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_case_no (case_no),
    UNIQUE KEY uk_active_case (active_key),
    KEY idx_status_created (status, created_at),
    KEY idx_owner (owner_id, created_at),
    KEY idx_contact (contact_id, created_at),
    KEY idx_wait_deadline (status, wait_period_end_at),
    CONSTRAINT fk_case_owner FOREIGN KEY (owner_id) REFERENCES sys_user (id),
    CONSTRAINT fk_case_contact FOREIGN KEY (contact_id) REFERENCES trusted_contact (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='授权案件';

-- ---------------------------------------------------------------------
-- 8. 审批记录（只增不改）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS authorization_approval;
CREATE TABLE authorization_approval (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    case_id         BIGINT UNSIGNED NOT NULL,
    approver_id     BIGINT UNSIGNED NOT NULL,
    action          VARCHAR(24)     NOT NULL COMMENT 'ACCEPT/APPROVE/REJECT/RESOLVE_DISPUTE',
    from_status     VARCHAR(24)     NOT NULL,
    to_status       VARCHAR(24)     NOT NULL,
    comment         VARCHAR(500)    NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_case_created (case_id, created_at),
    KEY idx_approver (approver_id, created_at),
    CONSTRAINT fk_appr_case FOREIGN KEY (case_id) REFERENCES authorization_case (id),
    CONSTRAINT fk_appr_user FOREIGN KEY (approver_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='审批记录';

-- ---------------------------------------------------------------------
-- 9. 案件与资产关联
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS authorization_asset;
CREATE TABLE authorization_asset (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    case_id     BIGINT UNSIGNED NOT NULL,
    asset_id    BIGINT UNSIGNED NOT NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_case_asset (case_id, asset_id),
    KEY idx_asset (asset_id),
    CONSTRAINT fk_aa_case FOREIGN KEY (case_id) REFERENCES authorization_case (id),
    CONSTRAINT fk_aa_asset FOREIGN KEY (asset_id) REFERENCES digital_asset (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='案件授权资产';

-- ---------------------------------------------------------------------
-- 10. 异议记录
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS dispute_record;
CREATE TABLE dispute_record (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    case_id         BIGINT UNSIGNED NOT NULL,
    submitter_id    BIGINT UNSIGNED NOT NULL,
    submitter_role  VARCHAR(16)     NOT NULL COMMENT 'OWNER/CONTACT/ADMIN',
    reason          VARCHAR(1000)   NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/RESOLVED',
    resolution      VARCHAR(500)    NULL COMMENT '管理员处理意见',
    resolved_by     BIGINT UNSIGNED NULL,
    resolved_at     DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_case_status (case_id, status),
    CONSTRAINT fk_dispute_case FOREIGN KEY (case_id) REFERENCES authorization_case (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='异议记录';

-- ---------------------------------------------------------------------
-- 11. 站内信（用户可见的通知正文）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS notification;
CREATE TABLE notification (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    receiver_id BIGINT UNSIGNED NOT NULL,
    biz_type    VARCHAR(32)     NOT NULL COMMENT 'HEARTBEAT_REMIND/CASE_APPROVED/AUTH_GRANTED/...',
    biz_id      BIGINT UNSIGNED NULL     COMMENT '关联业务对象 ID',
    title       VARCHAR(120)    NOT NULL,
    content     VARCHAR(1000)   NOT NULL,
    dedup_key   VARCHAR(128)    NULL     COMMENT '幂等键，如 heartbeat:3:2:remind',
    read_at     DATETIME        NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dedup_key (dedup_key),
    KEY idx_receiver_read (receiver_id, read_at, created_at),
    KEY idx_biz (biz_type, biz_id),
    CONSTRAINT fk_noti_user FOREIGN KEY (receiver_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='站内信';

-- ---------------------------------------------------------------------
-- 12. 通知投递任务（含重试与死信标记）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS notification_task;
CREATE TABLE notification_task (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    notification_id BIGINT UNSIGNED NOT NULL,
    channel         VARCHAR(16)     NOT NULL DEFAULT 'SITE' COMMENT 'SITE/EMAIL/SMS（v1 仅 SITE）',
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENDING/SUCCESS/FAILED/DEAD',
    retry_count     INT             NOT NULL DEFAULT 0,
    max_retry       INT             NOT NULL DEFAULT 5,
    next_retry_at   DATETIME        NULL COMMENT '下次重试时间，指数退避',
    last_error      VARCHAR(500)    NULL,
    event_id        CHAR(36)        NULL COMMENT '关联 outbox event_id，便于追踪',
    sent_at         DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_status_retry (status, next_retry_at),
    KEY idx_notification (notification_id),
    CONSTRAINT fk_ntask_noti FOREIGN KEY (notification_id) REFERENCES notification (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='通知投递任务';

-- ---------------------------------------------------------------------
-- 13. 事务发件箱
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS outbox_event;
CREATE TABLE outbox_event (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id        CHAR(36)        NOT NULL COMMENT 'UUID，消息幂等键',
    aggregate_type  VARCHAR(32)     NOT NULL COMMENT 'CASES/HEARTBEAT/CONTACT/...',
    aggregate_id    BIGINT UNSIGNED NOT NULL,
    event_type      VARCHAR(48)     NOT NULL COMMENT 'CASE_SUBMITTED/AUTH_GRANTED/...',
    payload         JSON            NOT NULL COMMENT '事件载荷，不含敏感明文',
    status          VARCHAR(16)     NOT NULL DEFAULT 'NEW' COMMENT 'NEW/PUBLISHED/FAILED',
    retry_count     INT             NOT NULL DEFAULT 0,
    next_retry_at   DATETIME        NULL,
    published_at    DATETIME        NULL,
    last_error      VARCHAR(500)    NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_id (event_id),
    KEY idx_status_next (status, next_retry_at),
    KEY idx_aggregate (aggregate_type, aggregate_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='事务发件箱';

-- ---------------------------------------------------------------------
-- 14. 消息消费记录（消费幂等）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS message_consume_record;
CREATE TABLE message_consume_record (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    message_id  VARCHAR(64)     NOT NULL COMMENT '事件 ID 或消息唯一键',
    queue_name  VARCHAR(64)     NOT NULL COMMENT '来源队列',
    status      VARCHAR(16)     NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED',
    error_msg   VARCHAR(500)    NULL,
    consumed_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 唯一约束即幂等边界：同一消息二次插入必然失败
    UNIQUE KEY uk_message_id (message_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='消息消费记录';

-- ---------------------------------------------------------------------
-- 15. 审计日志（只增不改，无外键）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS audit_log;
CREATE TABLE audit_log (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_id    BIGINT UNSIGNED NULL COMMENT '操作人；系统任务为 NULL',
    actor_role  VARCHAR(16)     NULL,
    action      VARCHAR(48)     NOT NULL COMMENT 'LOGIN_FAIL/ASSET_VIEW/CASE_APPROVED/...',
    object_type VARCHAR(32)     NOT NULL COMMENT 'ASSET/CONTACT/CASE/USER/...',
    object_id   BIGINT UNSIGNED NULL,
    result      VARCHAR(16)     NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILURE',
    request_id  VARCHAR(64)     NULL COMMENT '请求关联 ID',
    detail      JSON            NULL COMMENT '变更摘要，禁止写入密码/密钥/密文',
    client_ip   VARCHAR(45)     NULL,
    user_agent  VARCHAR(255)    NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_actor_created (actor_id, created_at),
    KEY idx_object (object_type, object_id, created_at),
    KEY idx_action_created (action, created_at),
    KEY idx_request (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='审计日志';

-- ---------------------------------------------------------------------
-- 16. 资产访问流水
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS asset_access_log;
CREATE TABLE asset_access_log (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    asset_id    BIGINT UNSIGNED NOT NULL,
    case_id     BIGINT UNSIGNED NULL COMMENT '通过授权案件访问时填写',
    accessor_id BIGINT UNSIGNED NOT NULL,
    access_type VARCHAR(16)     NOT NULL COMMENT 'OWNER_VIEW/CASE_VIEW',
    result      VARCHAR(16)     NOT NULL COMMENT 'SUCCESS/DENIED',
    deny_reason VARCHAR(64)     NULL COMMENT 'NOT_OWNER/NO_ACTIVE_CASE/CASE_REVOKED/...',
    client_ip   VARCHAR(45)     NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_asset_created (asset_id, created_at),
    KEY idx_accessor (accessor_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='资产访问流水';

-- ---------------------------------------------------------------------
-- 17. 登录流水
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS login_record;
CREATE TABLE login_record (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NULL COMMENT '登录成功时填写；用户不存在时为 NULL',
    username    VARCHAR(50)     NOT NULL,
    success     TINYINT         NOT NULL COMMENT '1 成功 0 失败',
    fail_reason VARCHAR(64)     NULL COMMENT 'BAD_CREDENTIAL/LOCKED/DISABLED',
    client_ip   VARCHAR(45)     NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_created (user_id, created_at),
    KEY idx_username_created (username, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='登录流水';

-- ---------------------------------------------------------------------
-- 18. 定时任务执行日志（任务可观测性）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS job_execution_log;
CREATE TABLE job_execution_log (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    job_name        VARCHAR(64)     NOT NULL COMMENT '任务名，如 heartbeatDueScan',
    instance_id     VARCHAR(64)     NOT NULL COMMENT '实例标识，多实例部署时区分',
    status          VARCHAR(16)     NOT NULL COMMENT 'SUCCESS/PARTIAL/FAILED',
    scanned_count   INT             NOT NULL DEFAULT 0 COMMENT '扫描到的记录数',
    processed_count INT             NOT NULL DEFAULT 0 COMMENT '成功处理数',
    failed_count    INT             NOT NULL DEFAULT 0 COMMENT '处理失败数',
    error_msg       VARCHAR(500)    NULL,
    started_at      DATETIME(3)     NOT NULL,
    finished_at     DATETIME(3)     NULL,
    duration_ms     INT             NULL,
    PRIMARY KEY (id),
    KEY idx_job_started (job_name, started_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='定时任务执行日志';

SET FOREIGN_KEY_CHECKS = 1;
