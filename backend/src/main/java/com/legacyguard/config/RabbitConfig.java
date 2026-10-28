package com.legacyguard.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑与可靠性配置。
 *
 * <p>拓扑（详见 docs/05 第 5.4 节）：
 * <pre>
 *   lg.event (topic)  ──[#]──>  lg.notify.queue  ──失败──>  lg.dlx (topic) ──> lg.notify.dlq
 * </pre>
 *
 * <p><b>可靠性必须分清四个层次</b>，它们不是同一件事：
 * <ol>
 *   <li>消息发出去了 —— send() 不抛异常</li>
 *   <li>Broker 收到了 —— Publisher Confirm 返回 ack</li>
 *   <li>消息被路由到队列了 —— ReturnsCallback 没有触发</li>
 *   <li>消费者处理完了 —— 手动 ack</li>
 * </ol>
 * 只配了 confirm 而没配 returns 时，routing key 写错的消息会被 Broker 静默丢弃，
 * 而 confirm 依然是 ack —— 这是最容易踩的坑。
 */
@Configuration
public class RabbitConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitConfig.class);

    /** 业务事件交换机 */
    public static final String EVENT_EXCHANGE = "lg.event";
    /** 死信交换机 */
    public static final String DLX_EXCHANGE = "lg.dlx";
    /** 通知队列 */
    public static final String NOTIFY_QUEUE = "lg.notify.queue";
    /** 通知死信队列 */
    public static final String NOTIFY_DLQ = "lg.notify.dlq";

    public static final String ROUTING_NOTIFY = "notify.#";
    public static final String ROUTING_DLQ = "dlq.#";

    @Bean
    public TopicExchange eventExchange() {
        // durable=true：Broker 重启后交换机仍存在
        return new TopicExchange(EVENT_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(DLX_EXCHANGE, true, false);
    }

    /**
     * 通知队列。
     *
     * <p>关键参数 {@code x-dead-letter-exchange}：消费者 {@code basicNack(requeue=false)} 后，
     * 消息不会凭空消失，而是被转到死信交换机。没有这个配置，消费失败的消息会直接丢失。
     *
     * <p>为什么是 {@code requeue=false}：{@code requeue=true} 会把消息立刻重新入队，
     * 在消费逻辑必然失败的情况下形成死循环，把 CPU 和日志打满。
     */
    @Bean
    public Queue notifyQueue() {
        return QueueBuilder.durable(NOTIFY_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "dlq.notify")
                .build();
    }

    @Bean
    public Queue notifyDlq() {
        return QueueBuilder.durable(NOTIFY_DLQ).build();
    }

    @Bean
    public Binding notifyBinding(Queue notifyQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(notifyQueue).to(eventExchange).with(ROUTING_NOTIFY);
    }

    @Bean
    public Binding dlqBinding(Queue notifyDlq, TopicExchange dlxExchange) {
        return BindingBuilder.bind(notifyDlq).to(dlxExchange).with("dlq.#");
    }

    @Bean
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        // 复用 Spring MVC 的 ObjectMapper，保证时间格式与服务端 API 一致
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * 发布确认与路由失败回调。
     *
     * <p>第二阶段只记录日志；第四阶段由 Outbox 发布器接入，
     * 根据 ack/nack 决定把 {@code outbox_event} 标记为 PUBLISHED 还是退回 NEW 等待重试。
     */
    @Bean
    public RabbitTemplateCustomizer rabbitTemplateCustomizer() {
        return template -> {
            template.setConfirmCallback((correlationData, ack, cause) -> {
                String id = correlationData != null ? correlationData.getId() : "unknown";
                if (ack) {
                    log.debug("消息已被 Broker 接收 id={}", id);
                } else {
                    log.warn("消息被 Broker 拒绝 id={} cause={}", id, cause);
                }
            });

            template.setReturnsCallback(returned -> {
                // 走到这里说明：Broker 收到了消息，但没有路由到任何队列（routing key 不匹配）
                log.error("消息路由失败，无人接收 exchange={} routingKey={} replyText={}",
                        returned.getExchange(), returned.getRoutingKey(), returned.getReplyText());
            });
        };
    }
}
