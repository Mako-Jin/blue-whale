package com.bluewhale.alert.service.impl;

import com.bluewhale.alert.config.AlertNotifyProperties;
import com.bluewhale.alert.entity.AlertRecord;
import com.bluewhale.alert.entity.AlertRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AlertNotifyServiceImpl} 邮件 / Webhook / 跳过路径单元测试。
 *
 * @author BlueWhale
 */
class AlertNotifyServiceImplTest {

    private final AlertNotifyProperties properties = new AlertNotifyProperties();

    private final ObjectProvider<JavaMailSender> mailSenderProvider = mock(ObjectProvider.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AlertNotifyServiceImpl notifyService =
            new AlertNotifyServiceImpl(properties, mailSenderProvider, objectMapper);

    @Test
    @DisplayName("EMAIL：启用且配置完整时发送邮件")
    void shouldSendEmail() {
        properties.getEmail().setEnabled(true);
        properties.getEmail().setFrom("alert@bluewhale.local");
        properties.getEmail().setTo(List.of("ops@bluewhale.local"));
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);

        notifyService.notify(rule("EMAIL"), record());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertEquals("[BlueWhale][WARN] CPU 使用率过高", message.getSubject());
        assertTrue(message.getText().contains("cpu_high"));
        assertTrue(message.getText().contains("CPU 使用率 92%"));
    }

    @Test
    @DisplayName("EMAIL：通道未启用时不取 JavaMailSender")
    void shouldSkipEmailWhenDisabled() {
        properties.getEmail().setEnabled(false);

        notifyService.notify(rule("EMAIL"), record());

        verify(mailSenderProvider, never()).getIfAvailable();
    }

    @Test
    @DisplayName("WEBHOOK：启用时 POST JSON 到配置地址")
    void shouldSendWebhook() throws IOException {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/hook", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();
        try {
            properties.getWebhook().setEnabled(true);
            properties.getWebhook().setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/hook");

            notifyService.notify(rule("WEBHOOK"), record());

            assertTrue(body.get().contains("\"ruleCode\":\"cpu_high\""));
            assertTrue(body.get().contains("\"status\":\"FIRING\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("NONE：不触发任何通道")
    void shouldSkipNoneChannel() {
        notifyService.notify(rule("NONE"), record());

        verifyNoInteractions(mailSenderProvider);
    }

    /**
     * 构造告警规则。
     *
     * @param channel 通知通道
     * @return 规则实体
     */
    private static AlertRule rule(String channel) {
        AlertRule rule = new AlertRule();
        rule.setRuleCode("cpu_high");
        rule.setRuleName("CPU 使用率过高");
        rule.setLevel("WARN");
        rule.setMetric("cpu.usage");
        rule.setTarget("whale-gateway");
        rule.setChannel(channel);
        return rule;
    }

    /**
     * 构造告警记录。
     *
     * @return 记录实体
     */
    private static AlertRecord record() {
        AlertRecord record = new AlertRecord();
        record.setRuleCode("cpu_high");
        record.setAlertLevel("WARN");
        record.setStatus("FIRING");
        record.setContent("CPU 使用率 92%");
        record.setTriggerTime(LocalDateTime.of(2026, 10, 25, 10, 0));
        return record;
    }
}
