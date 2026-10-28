-- =====================================================================
-- LegacyGuard 演示数据
--
-- 执行：mysql -u legacyguard -p legacy_guard < sql/demo-data.sql
--
-- ⚠️ 使用前必读
--
-- 1. 账号与密码（仅演示用途，生产环境严禁使用）：
--      admin   / Admin@12345      审核管理员
--      owner   / Owner@12345      资产所有者（持有下面三条资产）
--      contact / Contact@12345    可信联系人（与 owner 已建立生效关系）
--
-- 2. 资产密文是用【开发环境主密钥】加密的，AAD 绑定了固定资产 ID。
--    如果更换了 LG_KEK_KEYS（生产环境必须更换），这些密文将无法解密 ——
--    此时请删除演示资产，在前端重新创建。这正好演示了"密钥即数据"的约束。
--
-- 3. 本脚本会清空全部业务表，只在演示库中执行。
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE notification;
TRUNCATE TABLE message_consume_record;
TRUNCATE TABLE outbox_event;
TRUNCATE TABLE job_execution_log;
TRUNCATE TABLE dispute_record;
TRUNCATE TABLE authorization_approval;
TRUNCATE TABLE authorization_asset;
TRUNCATE TABLE authorization_case;
TRUNCATE TABLE digital_asset_secret;
TRUNCATE TABLE digital_asset;
TRUNCATE TABLE trusted_contact;
TRUNCATE TABLE heartbeat_record;
TRUNCATE TABLE heartbeat_confirm_config;
TRUNCATE TABLE asset_access_log;
TRUNCATE TABLE login_record;
TRUNCATE TABLE audit_log;
TRUNCATE TABLE sys_user;

SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------
-- 用户
-- ---------------------------------------------------------------------
INSERT INTO sys_user (id, username, password_hash, display_name, role, status,
                      failed_login_count, created_at, updated_at)
VALUES (1, 'admin',   '$2a$10$wzcpeYAeVE90Dfg4ljQYMe6pfcJfPfBZ0phmCD2WzjN.w9DEeXlri', '审核员', 'ADMIN', 0, 0, NOW(), NOW()),
       (2, 'owner',   '$2a$10$PBzk1Mwg8BMR5X6biqzbQ.zWMxCrOk9hlUNQhOtOV.PeiaCMQ6Rlm', '张老师', 'USER',  0, 0, NOW(), NOW()),
       (3, 'contact', '$2a$10$y5Z4ualxkLbQkjcE.FFTTOj5B/WwlB.1EvsJjrNZBfb4oMMCaD5aO', '李明',   'USER',  0, 0, NOW(), NOW());

-- ---------------------------------------------------------------------
-- 资产元数据
-- ---------------------------------------------------------------------
INSERT INTO digital_asset (id, owner_id, name, asset_type, description, status, version, created_at, updated_at)
VALUES
       (1, 2, '书房硬盘存放说明', 'NOTE', '家人照片备份的位置与口令提示', 0, 0, NOW(), NOW()),
       (2, 2, '云盘恢复方式', 'ACCOUNT_RECOVERY', '云盘账号的恢复信息', 0, 0, NOW(), NOW()),
       (3, 2, '摄影器材处置意愿', 'DOCUMENT', '对器材归属的说明', 0, 0, NOW(), NOW());

-- ---------------------------------------------------------------------
-- 资产密文（开发 KEK 加密，AAD = 'asset:<id>'）
-- ---------------------------------------------------------------------
INSERT INTO digital_asset_secret
    (asset_id, wrapped_dek, kek_version, cipher_text, iv, auth_tag, content_hash, plain_size, created_at, updated_at)
VALUES
       (1, '8pEnyg2ATRj2yaiOijvTLIZVgmW/KLPFeJT9sK4gC0eZseE3/O0z1uwNE9kbRgSwiEa2/QN0E/BZz2dA', 1,
        'fno07MxZR2yVR+x1qw8ZbL7s6fBnyRqKzSWbbDyOpmIT67OYq05n93LqthKMuzUvccxNgNwUftpw+xT9XWIDru5CvGGHg7thkSh/zRBAu6odDE2JmjnWRIRk/R+4N9rK/J/0pD7Y3rDaFsH3cRq6VoOm/6acPHf9UPJ96/Wo4NlFjJQA',
        'ec2E5lyC3LyPXlx/', '46lGloOlQSarNcq+evxe0w==',
        'df00fad68750941352b0f690fe06c080b793fb06c22aa42bb395af7595811b6c', 132, NOW(), NOW()),
       (2, '1xDlbZlTbsyEljEF+1bCL4X18QnQDRQCA+URCYyEYFUtjYGojyI6zoqdMXD5/cP3lAQEv6j6waGaa3OU', 1,
        'xlxOsVdKRVQ03upcyeOT/pBi0YjU0Mu1pyD+CeRUY0Nt8fbNXBy0rPbcEcWRk/jOviSkqCPyh7AecY0co5DW5clytA1+QIYITvdWEJZPAvW/04lvZepEZpAewKeIHWENuSw8vGW/',
        'qjoqS6LoieSzjEuk', 'I71Ea/O5ITlMYcGQCVzsuQ==',
        'c63ac619f21b144939396c6c4131f6d017b7a85128be34241feb0954092a98f2', 102, NOW(), NOW()),
       (3, 'lxdiIFFekAa4Yird5nYXHbhyRCyOSuw69wElKS5cbrWCSMkRcSDHW9YL39Uy45LNbkfsR8YL+sUE1nZi', 1,
        's8y0+qiPfpjdAniAwxB5RkcQ01reQburtIH+bEYt8c39SlfK/FafdarQCMZbTK0S2bRMKzf9kQwdyygSP/TDWGKGp6E8aMhkrrYvYaoaGLy5oQFfBb4SyYxx',
        'n00YJ0nKK13TS00t', 'td9AXdOz8rgIdDOXhyLfyw==',
        'c2e18531839d315a856c51d745d16e28856d9c8e0f74152680790486fd73f085', 90, NOW(), NOW());

-- ---------------------------------------------------------------------
-- 可信联系人关系：owner → contact，已生效
-- ---------------------------------------------------------------------
INSERT INTO trusted_contact (id, owner_id, contact_user_id, status, note,
                             invited_at, responded_at, version, created_at, updated_at)
VALUES (1, 2, 3, 'ACTIVE', '弟弟', NOW(), NOW(), 0, NOW(), NOW());

-- ---------------------------------------------------------------------
-- 状态确认配置
--
-- 默认设为 ACTIVE（正常计时），方便演示"按时确认"。
-- 想直接演示授权流程，把状态改成 REVIEWING（模拟"失联已核验通过"）：
--
--   UPDATE heartbeat_confirm_config
--      SET status = 'REVIEWING', next_deadline_at = DATE_SUB(NOW(), INTERVAL 30 DAY)
--    WHERE user_id = 2;
-- ---------------------------------------------------------------------
INSERT INTO heartbeat_confirm_config
    (user_id, cycle_days, reminder_rounds, reminder_interval_hours, status, cycle_no,
     last_confirm_at, next_deadline_at, reminder_count, version, created_at, updated_at)
VALUES (2, 90, 3, 72, 'ACTIVE', 1,
        NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), 0, 0, NOW(), NOW());

-- ---------------------------------------------------------------------
-- 演示路径
--
--   1. contact 登录 → 授权案件 → 新建申请（勾选资产）→ 提交
--   2. admin   登录 → 案件审核 → 受理 → 批准（进入等待期）
--   3. 等等待期结束，或 admin 点「触发等待期扫描」
--   4. contact 打开案件详情 → 查看解密后的资产内容
--   5. owner   登录 → 案件详情 → 撤销授权 → contact 立即失去访问权
-- ---------------------------------------------------------------------

SELECT CONCAT('演示数据已导入：用户 ', (SELECT COUNT(*) FROM sys_user),
              ' 个，资产 ', (SELECT COUNT(*) FROM digital_asset),
              ' 条，联系人关系 ', (SELECT COUNT(*) FROM trusted_contact), ' 条') AS result;
