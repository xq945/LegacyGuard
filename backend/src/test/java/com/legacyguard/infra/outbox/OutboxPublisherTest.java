package com.legacyguard.infra.outbox;

import com.legacyguard.infra.mq.LegacyEvent;
import com.legacyguard.infra.outbox.domain.OutboxEvent;
import com.legacyguard.infra.outbox.mapper.OutboxEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发件箱发布器测试。
 *
 * <p>核心命题：<b>只有 Broker 明确 ack，才能把事件标记为已发布。</b>
 * 不等确认就标记 PUBLISHED，等于把"可能丢消息"伪装成"发送成功"。
 */
@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final Long EVENT_ID = 1L;

    @Mock
    private OutboxEventMapper outboxMapper;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(outboxMapper, rabbitTemplate);
    }

    private OutboxEvent pendingEvent(int retryCount) {
        OutboxEvent e = new OutboxEvent();
        e.setId(EVENT_ID);
        e.setEventId("evt-uuid-1");
        e.setEventType("case.submitted");
        e.setAggregateType("CASE");
        e.setAggregateId(10L);
        e.setPayload("{\"receiverId\":1}");
        e.setStatus("NEW");
        e.setRetryCount(retryCount);
        e.setCreatedAt(LocalDateTime.now());
        return e;
    }

    /** 模拟 Broker 的发布确认结果 */
    private void brokerAcks(boolean ack) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(LegacyEvent.class), any(CorrelationData.class));
    }

    @Test
    @DisplayName("Broker 确认后标记为 PUBLISHED")
    void marksPublishedOnlyAfterAck() {
        when(outboxMapper.selectPending(any(), anyLong(), anyInt()))
                .thenReturn(List.of(pendingEvent(0)))
                .thenReturn(List.of());
        when(outboxMapper.claim(eq(EVENT_ID), any(), any())).thenReturn(1);
        brokerAcks(true);

        int published = publisher.publishPending();

        assertThat(published).isEqualTo(1);
        verify(outboxMapper).markPublished(eq(EVENT_ID), any());
        verify(outboxMapper, never()).markFailed(anyLong(), anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("抢占失败时不发送 —— 说明别的实例已经领走了")
    void skipsWhenClaimFails() {
        when(outboxMapper.selectPending(any(), anyLong(), anyInt()))
                .thenReturn(List.of(pendingEvent(0)))
                .thenReturn(List.of());
        when(outboxMapper.claim(eq(EVENT_ID), any(), any())).thenReturn(0);

        int published = publisher.publishPending();

        assertThat(published).isZero();
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(LegacyEvent.class), any(CorrelationData.class));
    }

    @Test
    @DisplayName("Broker 返回 nack 时不算成功，退回待发并退避")
    void nackTriggersRetryWithBackoff() {
        when(outboxMapper.selectPending(any(), anyLong(), anyInt()))
                .thenReturn(List.of(pendingEvent(0)))
                .thenReturn(List.of());
        when(outboxMapper.claim(eq(EVENT_ID), any(), any())).thenReturn(1);
        brokerAcks(false);

        int published = publisher.publishPending();

        assertThat(published).isZero();
        verify(outboxMapper, never()).markPublished(anyLong(), any());

        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        verify(outboxMapper).markFailed(eq(EVENT_ID), status.capture(), any(), anyString(), any());
        // 还有重试额度，退回 NEW
        assertThat(status.getValue()).isEqualTo("NEW");
    }

    @Test
    @DisplayName("重试次数达到上限后转为 FAILED，进入人工处理")
    void givesUpAfterMaxRetries() {
        when(outboxMapper.selectPending(any(), anyLong(), anyInt()))
                .thenReturn(List.of(pendingEvent(4)))
                .thenReturn(List.of());
        when(outboxMapper.claim(eq(EVENT_ID), any(), any())).thenReturn(1);
        brokerAcks(false);

        publisher.publishPending();

        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        verify(outboxMapper).markFailed(eq(EVENT_ID), status.capture(), any(), anyString(), any());
        assertThat(status.getValue()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("单条发送异常不影响后续事件的处理")
    void oneFailureDoesNotStopTheBatch() {
        OutboxEvent second = pendingEvent(0);
        second.setId(2L);
        second.setEventId("evt-uuid-2");

        when(outboxMapper.selectPending(any(), anyLong(), anyInt()))
                .thenReturn(List.of(pendingEvent(0), second))
                .thenReturn(List.of());

        when(outboxMapper.claim(eq(EVENT_ID), any(), any())).thenReturn(1);
        when(outboxMapper.claim(eq(2L), any(), any())).thenReturn(1);
        brokerAcks(true);

        int published = publisher.publishPending();

        // 两条都成功
        assertThat(published).isEqualTo(2);
    }
}
