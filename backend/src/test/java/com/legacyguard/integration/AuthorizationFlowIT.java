package com.legacyguard.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.images.builder.Transferable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端集成测试：真实 MySQL + Redis + RabbitMQ。
 *
 * <p>与单元测试的分工：单元测试（Mockito）验证<b>逻辑分支</b>，
 * 这里验证<b>组件之间的契约</b> —— SQL 是否真的能执行、条件更新是否真的影响 0 行、
 * 加密存储的密文是否真的能解回、事务边界是否符合预期。
 *
 * <p>数据库表结构直接读取项目根的 {@code sql/schema.sql}，
 * 不复制副本，避免两边不同步。
 *
 * <p><b>前提</b>：本地需已有 {@code mysql:8.0}、{@code redis:7}、
 * {@code rabbitmq:3.12-management} 三个镜像（执行过 {@code docker compose up} 就有）。
 * 这里刻意使用 {@code PullPolicy.neverPull()} —— 默认策略会去远端校验镜像，
 * 在无法访问 Docker Hub 的环境里会卡满两分钟再失败。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthorizationFlowIT {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("legacy_guard")
            .withUsername("legacyguard")
            .withPassword("testpass")
            .withImagePullPolicy(imageName -> false);   // 只用本地镜像，不去远端校验

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.12-management")
            .withImagePullPolicy(imageName -> false);   // 只用本地镜像，不去远端校验

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7").withExposedPorts(6379)
                    .withImagePullPolicy(imageName -> false);   // 只用本地镜像，不去远端校验

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("legacyguard.jwt.secret",
                () -> "dGVzdC1zZWNyZXQtZm9yLWludGVncmF0aW9uLXRlc3RzLTMyKw==");
        registry.add("legacyguard.crypto.keys",
                () -> "1=dGVzdC1rZWstZm9yLWludGVncmF0aW9uLXRlc3QtMzI=");
        // 集成测试关闭定时任务，避免后台扫描干扰断言
        registry.add("legacyguard.job.enabled", () -> "false");
    }

    static {
        // withCopyToContainer 必须在容器启动前调用，因此放在 static 块里。
        // 直接读取项目根的 schema.sql，不维护测试专用副本。
        try {
            String schema = Files.readString(Path.of("../sql/schema.sql"));
            MYSQL.withCopyToContainer(Transferable.of(schema),
                    "/docker-entrypoint-initdb.d/01-schema.sql");
        } catch (Exception e) {
            throw new IllegalStateException("无法读取 sql/schema.sql", e);
        }
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    // ==================================================================
    // 工具
    // ==================================================================

    private JsonNode call(HttpMethod method, String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        ResponseEntity<String> response = rest.exchange(path, method,
                new HttpEntity<>(body, headers), String.class);
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("响应不是合法 JSON: " + response.getBody(), e);
        }
    }

    private int status(HttpMethod method, String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class)
                .getStatusCode().value();
    }

    private String registerAndLogin(String username) {
        call(HttpMethod.POST, "/api/auth/register",
                Map.of("username", username, "password", "Str0ngPass", "displayName", username), null);
        JsonNode login = call(HttpMethod.POST, "/api/auth/login",
                Map.of("username", username, "password", "Str0ngPass"), null);
        return login.at("/data/accessToken").asText();
    }

    // ==================================================================
    // 测试
    // ==================================================================

    @Test
    @Order(1)
    @DisplayName("注册 → 登录 → 创建资产 → 解密读取，密文在库中不可读")
    void encryptStoreAndReadBack() throws Exception {
        String token = registerAndLogin("it_user1");

        JsonNode created = call(HttpMethod.POST, "/api/assets",
                Map.of("name", "集成测试资产", "assetType", "NOTE",
                        "content", "这段内容必须以密文形式落库"), token);
        long assetId = created.at("/data/id").asLong();
        assertThat(assetId).isPositive();

        // 库中存的是密文
        String cipher = jdbc.queryForObject(
                "SELECT cipher_text FROM digital_asset_secret WHERE asset_id = ?", String.class, assetId);
        assertThat(cipher).doesNotContain("这段内容");

        // 接口能解回明文
        JsonNode content = call(HttpMethod.GET, "/api/assets/" + assetId + "/content", null, token);
        assertThat(content.at("/data/content").asText()).isEqualTo("这段内容必须以密文形式落库");
        assertThat(content.at("/data/accessType").asText()).isEqualTo("OWNER_VIEW");
    }

    @Test
    @Order(2)
    @DisplayName("越权访问他人资产返回 403，且访问流水记录拒绝原因")
    void strangerDeniedWithAccessLog() {
        String ownerToken = registerAndLogin("it_owner2");
        String strangerToken = registerAndLogin("it_stranger2");

        long assetId = call(HttpMethod.POST, "/api/assets",
                Map.of("name", "私有资产", "assetType", "NOTE", "content", "私有内容"), ownerToken)
                .at("/data/id").asLong();

        int denied = status(HttpMethod.GET, "/api/assets/" + assetId + "/content", null, strangerToken);
        assertThat(denied).isEqualTo(403);

        Integer deniedLogs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM asset_access_log WHERE asset_id = ? AND result = 'DENIED'",
                Integer.class, assetId);
        assertThat(deniedLogs).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(3)
    @DisplayName("完整授权闭环：建案 → 审批 → 等待期生效 → 联系人可读 → 撤销后 403")
    void fullAuthorizationLifecycle() {
        String ownerToken = registerAndLogin("it_owner3");
        String contactToken = registerAndLogin("it_contact3");
        String adminToken = registerAndLogin("it_admin3");

        long ownerId = call(HttpMethod.GET, "/api/users/me", null, ownerToken).at("/data/id").asLong();
        jdbc.update("UPDATE sys_user SET role = 'ADMIN' WHERE username = 'it_admin3'");
        // 角色变了，令牌里的 role 还是 USER，重新登录
        adminToken = call(HttpMethod.POST, "/api/auth/login",
                Map.of("username", "it_admin3", "password", "Str0ngPass"), null)
                .at("/data/accessToken").asText();

        long assetId = call(HttpMethod.POST, "/api/assets",
                Map.of("name", "授权演示资产", "assetType", "NOTE", "content", "授权后才能看到的内容"),
                ownerToken).at("/data/id").asLong();

        long relationId = call(HttpMethod.POST, "/api/contacts/invitations",
                Map.of("username", "it_contact3"), ownerToken).at("/data/id").asLong();
        call(HttpMethod.POST, "/api/contacts/invitations/" + relationId + "/accept", null, contactToken);

        // 所有者未通过失联核验 → 不能建案
        int tooEarly = status(HttpMethod.POST, "/api/authorizations",
                Map.of("contactId", relationId, "assetIds", java.util.List.of(assetId), "reason", "申请"),
                contactToken);
        assertThat(tooEarly).isEqualTo(422);

        // 模拟失联已核验
        jdbc.update("""
                INSERT INTO heartbeat_confirm_config
                    (user_id, cycle_days, reminder_rounds, reminder_interval_hours, status, cycle_no,
                     last_confirm_at, next_deadline_at, reminder_count, version, created_at, updated_at)
                VALUES (?, 90, 3, 72, 'REVIEWING', 1, NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), 0, 0, NOW(), NOW())
                ON DUPLICATE KEY UPDATE status = 'REVIEWING'
                """, ownerId);

        long caseId = call(HttpMethod.POST, "/api/authorizations",
                Map.of("contactId", relationId, "assetIds", java.util.List.of(assetId), "reason", "申请"),
                contactToken).at("/data/id").asLong();

        call(HttpMethod.POST, "/api/authorizations/" + caseId + "/submit", null, contactToken);
        call(HttpMethod.POST, "/api/admin/authorizations/" + caseId + "/accept", null, adminToken);
        call(HttpMethod.POST, "/api/admin/authorizations/" + caseId + "/approve",
                Map.of("comment", "材料齐全"), adminToken);

        // 批准只到等待期，不是生效
        String afterApprove = jdbc.queryForObject(
                "SELECT status FROM authorization_case WHERE id = ?", String.class, caseId);
        assertThat(afterApprove).isEqualTo("WAITING_PERIOD");

        // 等待期内联系人还读不到
        assertThat(status(HttpMethod.GET, "/api/assets/" + assetId + "/content", null, contactToken)).isEqualTo(403);

        // 把等待期改到过去，触发扫描
        jdbc.update("UPDATE authorization_case SET wait_period_end_at = DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE id = ?",
                caseId);
        call(HttpMethod.POST, "/api/admin/authorizations/scan-expired", null, adminToken);

        assertThat(jdbc.queryForObject("SELECT status FROM authorization_case WHERE id = ?", String.class, caseId))
                .isEqualTo("AUTHORIZED");

        // 生效后可以读
        int allowed = status(HttpMethod.GET, "/api/assets/" + assetId + "/content", null, contactToken);
        assertThat(allowed).isEqualTo(200);

        // 撤销后立即失效（无缓存）
        call(HttpMethod.POST, "/api/authorizations/" + caseId + "/revoke", null, ownerToken);
        assertThat(status(HttpMethod.GET, "/api/assets/" + assetId + "/content", null, contactToken)).isEqualTo(403);
    }

    @Test
    @Order(4)
    @DisplayName("异议挡住等待期生效")
    void disputeBlocksAuthorization() {
        String ownerToken = registerAndLogin("it_owner4");
        String contactToken = registerAndLogin("it_contact4");
        String adminToken = registerAndLogin("it_admin4");

        long ownerId = call(HttpMethod.GET, "/api/users/me", null, ownerToken).at("/data/id").asLong();
        jdbc.update("UPDATE sys_user SET role = 'ADMIN' WHERE username = 'it_admin4'");
        adminToken = call(HttpMethod.POST, "/api/auth/login",
                Map.of("username", "it_admin4", "password", "Str0ngPass"), null)
                .at("/data/accessToken").asText();

        long assetId = call(HttpMethod.POST, "/api/assets",
                Map.of("name", "异议测试资产", "assetType", "NOTE", "content", "内容"), ownerToken)
                .at("/data/id").asLong();
        long relationId = call(HttpMethod.POST, "/api/contacts/invitations",
                Map.of("username", "it_contact4"), ownerToken).at("/data/id").asLong();
        call(HttpMethod.POST, "/api/contacts/invitations/" + relationId + "/accept", null, contactToken);

        jdbc.update("""
                INSERT INTO heartbeat_confirm_config
                    (user_id, cycle_days, reminder_rounds, reminder_interval_hours, status, cycle_no,
                     last_confirm_at, next_deadline_at, reminder_count, version, created_at, updated_at)
                VALUES (?, 90, 3, 72, 'REVIEWING', 1, NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), 0, 0, NOW(), NOW())
                ON DUPLICATE KEY UPDATE status = 'REVIEWING'
                """, ownerId);

        long caseId = call(HttpMethod.POST, "/api/authorizations",
                Map.of("contactId", relationId, "assetIds", java.util.List.of(assetId), "reason", "申请"),
                contactToken).at("/data/id").asLong();
        call(HttpMethod.POST, "/api/authorizations/" + caseId + "/submit", null, contactToken);
        call(HttpMethod.POST, "/api/admin/authorizations/" + caseId + "/accept", null, adminToken);
        call(HttpMethod.POST, "/api/admin/authorizations/" + caseId + "/approve",
                Map.of("comment", "同意"), adminToken);

        // 所有者提异议
        call(HttpMethod.POST, "/api/authorizations/" + caseId + "/disputes",
                Map.of("reason", "我本人健在"), ownerToken);
        assertThat(jdbc.queryForObject("SELECT status FROM authorization_case WHERE id = ?", String.class, caseId))
                .isEqualTo("DISPUTED");

        // 即使等待期已过，扫描也不会生效
        jdbc.update("UPDATE authorization_case SET wait_period_end_at = DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE id = ?",
                caseId);
        JsonNode scan = call(HttpMethod.POST, "/api/admin/authorizations/scan-expired", null, adminToken);
        assertThat(scan.at("/data/authorized").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM authorization_case WHERE id = ?", String.class, caseId))
                .isEqualTo("DISPUTED");
    }

    @Test
    @Order(5)
    @DisplayName("通知经发件箱投递到 MQ，最终写入站内信")
    void notificationDeliveredThroughOutbox() throws Exception {
        String ownerToken = registerAndLogin("it_owner5");
        String contactToken = registerAndLogin("it_contact5");

        long relationId = call(HttpMethod.POST, "/api/contacts/invitations",
                Map.of("username", "it_contact5"), ownerToken).at("/data/id").asLong();

        // 事件已写入发件箱
        Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE status IN ('NEW','PUBLISHED')", Integer.class);
        assertThat(pending).isGreaterThanOrEqualTo(1);

        // 手动触发发布（定时任务在测试中被关闭）
        call(HttpMethod.POST, "/api/admin/jobs/outbox/publish-now", null, adminTokenOrNull());

        // 等待消费者落库
        long deadline = System.currentTimeMillis() + 15_000;
        int notifications = 0;
        while (System.currentTimeMillis() < deadline) {
            notifications = jdbc.queryForObject("SELECT COUNT(*) FROM notification", Integer.class);
            if (notifications > 0) {
                break;
            }
            Thread.sleep(500);
        }

        assertThat(notifications).isGreaterThan(0);
        assertThat(relationId).isPositive();
    }

    /** 发布接口需要管理员权限，这里临时提升一个账号 */
    private String adminTokenOrNull() {
        jdbc.update("UPDATE sys_user SET role = 'ADMIN' WHERE username = 'it_admin3'");
        JsonNode login = call(HttpMethod.POST, "/api/auth/login",
                Map.of("username", "it_admin3", "password", "Str0ngPass"), null);
        return login.at("/data/accessToken").asText();
    }
}
