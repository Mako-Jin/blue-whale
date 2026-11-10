package com.bluewhale.doc.application.service.impl;

import com.bluewhale.doc.application.service.DocNotifyEvent;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.infrastructure.client.DocMessageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocNotificationListener} 通知发送与渠道解析行为断言。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocNotificationListenerTest {

    @Mock
    private DocMessageClient messageClient;

    private DocProperties properties;

    private DocNotificationListener listener;

    @BeforeEach
    void setUp() {
        properties = new DocProperties();
        listener = new DocNotificationListener(messageClient, properties);
    }

    @Test
    void onNotifyShouldSkipWhenNotifyDisabled() {
        properties.setNotifyEnabled(false);

        listener.onNotify(event());

        verify(messageClient, never()).send(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void onNotifyShouldSendWithDefaultChannelWhenChannelsBlank() {
        properties.setNotifyEnabled(true);
        properties.setNotifyChannels("   ");

        listener.onNotify(event());

        verify(messageClient).send(1L, "DOC_COMMENT", "7", "doc:comment:5:9",
                "评论提醒", "内容", List.of("INAPP"), List.of(9L));
    }

    @Test
    void onNotifyShouldParseChannelsAndDropBlankSegments() {
        properties.setNotifyEnabled(true);
        properties.setNotifyChannels(" INAPP , SMS , ,");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        listener.onNotify(event());

        verify(messageClient).send(eq(1L), eq("DOC_COMMENT"), eq("7"), eq("doc:comment:5:9"),
                eq("评论提醒"), eq("内容"), captor.capture(), eq(List.of(9L)));
        assertThat(captor.getValue()).containsExactly("INAPP", "SMS");
    }

    @Test
    void onNotifyShouldSwallowSendFailure() {
        properties.setNotifyEnabled(true);
        when(messageClient.send(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("message down"));

        assertThatCode(() -> listener.onNotify(event())).doesNotThrowAnyException();
    }

    /**
     * 构造通知事件。
     *
     * @return 通知事件
     */
    private DocNotifyEvent event() {
        return new DocNotifyEvent(1L, "DOC_COMMENT", "7", "doc:comment:5:9",
                "评论提醒", "内容", List.of(9L));
    }
}
