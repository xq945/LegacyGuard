package com.legacyguard.infra.mq;

import com.legacyguard.config.RabbitConfig;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 通知队列消费者。
 *
 * <p><b>手动 ack</b>：处理成功才确认。失败时 {@code basicNack(requeue=false)}，
 * 消息进死信队列，而不是无限重投。
 *
 * <p><b>为什么不用 {@code requeue=true}</b>：消费逻辑要是必然失败（比如载荷格式错误），
 * 立刻重投会形成死循环，把 CPU 和磁盘日志打满。有限重试交给发布器和发件箱的退避机制，
 * 这里只负责"这一条要么处理掉、要么老实承认失败"。
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationEventHandler handler;

    public NotificationConsumer(NotificationEventHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(queues = RabbitConfig.NOTIFY_QUEUE, ackMode = "MANUAL")
    public void onMessage(LegacyEvent event, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            handler.handle(event);
            channel.basicAck(deliveryTag, false);

        } catch (RuntimeException e) {
            log.error("消息处理失败，转入死信队列 eventId={} type={}",
                    event == null ? "unknown" : event.eventId(),
                    event == null ? "unknown" : event.eventType(), e);
            // requeue=false：不重投，交给死信交换器
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
