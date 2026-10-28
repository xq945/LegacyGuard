package com.legacyguard.infra.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legacyguard.infra.mq.mapper.MessageConsumeRecordMapper;
import com.legacyguard.module.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 消费幂等测试。
 *
 * <p>核心命题：<b>同一条消息处理两次，业务效果只能有一次。</b>
 */
@ExtendWith(MockitoExtension.class)
class NotificationEventHandlerTest {

    @Mock
    private MessageConsumeRecordMapper consumeMapper;
    @Mock
    private NotificationService notificationService;

    private NotificationEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new NotificationEventHandler(consumeMapper, notificationService, new ObjectMapper());
    }

    private LegacyEvent event(String payload) {
        return new LegacyEvent("evt-1", "case.submitted", "CASE", 10L, payload, LocalDateTime.now());
    }

    private String validPayload() {
        return """
                {"receiverId":1,"bizType":"CASE_SUBMITTED","bizId":10,
                 "title":"有人提交了授权申请","content":"内容","dedupKey":"case:submit:10"}
                """;
    }

    @Test
    @DisplayName("首次处理会写入站内信")
    void firstTimeWritesNotification() {
        when(consumeMapper.tryRecord(anyString(), anyString(), anyString(), any())).thenReturn(1);

        handler.handle(event(validPayload()));

        verify(notificationService).send(eq(1L), eq("CASE_SUBMITTED"), eq(10L),
                anyString(), anyString(), eq("case:submit:10"));
    }

    @Test
    @DisplayName("重复消息直接跳过，不产生第二条站内信")
    void duplicateMessageSkipped() {
        // 唯一索引插入失败 → 说明这条消息处理过了
        when(consumeMapper.tryRecord(anyString(), anyString(), anyString(), any())).thenReturn(0);

        handler.handle(event(validPayload()));

        verify(notificationService, never()).send(any(), anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("载荷解析失败时抛异常，交由死信队列处理")
    void malformedPayloadThrows() {
        when(consumeMapper.tryRecord(anyString(), anyString(), anyString(), any())).thenReturn(1);

        assertThatThrownBy(() -> handler.handle(event("{not-json")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("载荷解析失败");

        verify(notificationService, never()).send(any(), anyString(), any(), anyString(), anyString(), anyString());
    }
}
