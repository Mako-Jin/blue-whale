package com.bluewhale.message.channel.impl;

import com.bluewhale.message.channel.ChannelMessage;
import com.bluewhale.message.channel.ChannelResult;
import com.bluewhale.message.channel.MessageChannelSender;
import com.bluewhale.message.config.MessageChannelProperties;
import com.bluewhale.message.enums.MessageChannel;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * 邮件渠道适配器。
 *
 * <p>{@code mock=true} 时不发请求；未启用、缺少发件人或收件人、未配置 JavaMailSender 时
 * 返回 {@code SKIPPED}；真实发送失败返回 {@code FAILED}，由发送内核记录，不抛出。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailChannelSender implements MessageChannelSender {

    private final MessageChannelProperties properties;

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Override
    public MessageChannel channel() {
        return MessageChannel.EMAIL;
    }

    @Override
    public ChannelResult send(ChannelMessage message) {
        MessageChannelProperties.Email email = properties.getEmail();
        if (properties.isMock()) {
            return ChannelResult.mocked();
        }
        if (!email.isEnabled()) {
            return ChannelResult.skipped("邮件渠道未启用");
        }
        if (!StringUtils.hasText(email.getFrom())) {
            return ChannelResult.skipped("未配置发件人");
        }
        if (message.getRecipient() == null || !StringUtils.hasText(message.getRecipient().getEmail())) {
            return ChannelResult.skipped("缺少收件人邮箱");
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            return ChannelResult.skipped("未配置 spring.mail.host");
        }
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            helper.setFrom(email.getFrom());
            helper.setTo(message.getRecipient().getEmail());
            helper.setSubject(message.getTitle() == null ? "" : message.getTitle());
            helper.setText(message.getContent() == null ? "" : message.getContent(), false);
            mailSender.send(mimeMessage);
            log.info("消息邮件已发送: messageId={}, to={}", message.getMessageId(),
                    message.getRecipient().getEmail());
            return ChannelResult.sent(null);
        } catch (Exception e) {
            log.error("消息邮件发送失败: messageId={}, reason={}", message.getMessageId(), e.getMessage(), e);
            return ChannelResult.failed("邮件发送失败: " + e.getMessage());
        }
    }
}
