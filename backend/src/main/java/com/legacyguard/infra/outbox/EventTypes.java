package com.legacyguard.infra.outbox;

/**
 * 业务事件类型常量。
 *
 * <p>也是 RabbitMQ 的路由键前缀。命名规则 {@code 领域.动作}，
 * 方便用通配绑定（{@code notify.#}）而不用逐个列出。
 */
public final class EventTypes {

    public static final String HEARTBEAT_REMIND = "heartbeat.remind";
    public static final String HEARTBEAT_VERIFYING = "heartbeat.verifying";

    public static final String CONTACT_INVITED = "contact.invited";
    public static final String CONTACT_ACTIVATED = "contact.activated";

    public static final String CASE_SUBMITTED = "case.submitted";
    public static final String CASE_WAITING = "case.waiting";
    public static final String CASE_REJECTED = "case.rejected";
    public static final String CASE_AUTHORIZED = "case.authorized";
    public static final String CASE_REVOKED = "case.revoked";
    public static final String CASE_DISPUTED = "case.disputed";

    private EventTypes() {
    }
}
