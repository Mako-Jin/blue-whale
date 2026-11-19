package com.bluewhale.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.message.channel.ChannelGuard;
import com.bluewhale.message.channel.ChannelMessage;
import com.bluewhale.message.channel.ChannelResult;
import com.bluewhale.message.channel.MessageChannelSender;
import com.bluewhale.message.config.MessageChannelProperties;
import com.bluewhale.message.converter.MessageConverter;
import com.bluewhale.message.dto.MessageRecipient;
import com.bluewhale.message.dto.SendMessageRequest;
import com.bluewhale.message.po.MessageDeliveryPo;
import com.bluewhale.message.po.MessageRecordPo;
import com.bluewhale.message.po.MessageTemplatePo;
import com.bluewhale.message.enums.DeliveryStatus;
import com.bluewhale.message.enums.MessageChannel;
import com.bluewhale.message.enums.MessageStatus;
import com.bluewhale.message.mapper.MessageDeliveryMapper;
import com.bluewhale.message.mapper.MessageRecordMapper;
import com.bluewhale.message.mapper.MessageTemplateMapper;
import com.bluewhale.message.service.MessageSendService;
import com.bluewhale.message.support.CallbackUrlGuard;
import com.bluewhale.message.support.MessageMetrics;
import com.bluewhale.message.support.MessageStatusAggregator;
import com.bluewhale.message.support.TemplateRenderer;
import com.bluewhale.message.vo.MessageDeliveryVO;
import com.bluewhale.message.vo.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 消息发送服务实现。
 *
 * <p>发送流程刻意不包在跨出站长事务中：先落消息记录，再逐渠道调用并写投递记录，
 * 最后按投递结果回写消息状态。任一渠道失败只影响该条投递，不阻断其它渠道。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageSendServiceImpl implements MessageSendService {

    /** 模板启用状态 */
    private static final int TEMPLATE_ENABLED = 1;

    /** 渠道侧消息标识最大长度（列宽 128） */
    private static final int PROVIDER_MESSAGE_ID_MAX_LENGTH = 128;

    private final MessageRecordMapper recordMapper;

    private final MessageDeliveryMapper deliveryMapper;

    private final MessageTemplateMapper templateMapper;

    private final ObjectMapper objectMapper;

    private final MessageChannelProperties properties;

    private final List<MessageChannelSender> senders;

    private final ChannelGuard channelGuard;

    private final MessageMetrics metrics;

    /** 渠道适配器缓存：Bean 生命周期内注册表不变，首次使用时构建一次 */
    private volatile Map<MessageChannel, MessageChannelSender> senderCache;

    @Override
    public SendMessageResult send(SendMessageRequest request) {
        Long tenantId = request.getTenantId() == null ? TenantContext.get() : request.getTenantId();
        if (tenantId == null || tenantId <= 0L) {
            throw new BizException(ErrorCode.PARAM_ERROR, "缺少租户ID");
        }
        Long previous = TenantContext.get();
        TenantContext.set(tenantId);
        try {
            if (StringUtils.hasText(request.getIdempotencyKey())) {
                MessageRecordPo existing = selectByIdempotencyKey(request.getIdempotencyKey());
                if (existing != null) {
                    return buildResult(existing, true);
                }
            }
            MessageTemplatePo template = resolveTemplate(request.getTemplateCode());
            MessageRecordPo record = buildRecord(request, template);
            try {
                recordMapper.insert(record);
            } catch (DuplicateKeyException e) {
                MessageRecordPo existing = selectByIdempotencyKey(request.getIdempotencyKey());
                if (existing != null) {
                    log.info("消息幂等命中（并发）: messageId={}, key={}",
                            existing.getId(), request.getIdempotencyKey());
                    return buildResult(existing, true);
                }
                throw e;
            }
            List<MessageDeliveryPo> deliveries = dispatch(record, request);
            record.setStatus(aggregateStatus(deliveries).name());
            recordMapper.updateById(record);
            return buildResult(record, false, deliveries);
        } finally {
            TenantContext.set(previous);
        }
    }

    @Override
    public SendMessageResult retry(Long messageId) {
        MessageRecordPo record = messageId == null ? null : recordMapper.selectById(messageId);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "消息不存在: " + messageId);
        }
        if (properties.isMock()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "mock 模式下不允许重试：不会产生真实投递");
        }
        List<MessageDeliveryPo> failed = selectDeliveries(record.getId()).stream()
                .filter(delivery -> DeliveryStatus.FAILED.name().equals(delivery.getStatus()))
                .toList();
        if (failed.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "没有可重试的失败投递");
        }
        Map<MessageChannel, MessageChannelSender> senderMap = senderMap();
        Map<String, Object> payload = parsePayload(record.getPayloadJson());
        for (MessageDeliveryPo delivery : failed) {
            MessageChannel channel = MessageChannel.parse(delivery.getChannel());
            if (channel == null) {
                updateDelivery(delivery, ChannelResult.failed("无法识别的消息渠道: " + delivery.getChannel()));
                continue;
            }
            MessageChannelSender sender = senderMap.get(channel);
            if (sender == null) {
                updateDelivery(delivery, ChannelResult.failed("渠道未实现: " + channel.name()));
                continue;
            }
            MessageRecipient recipient = rebuildRecipient(channel, delivery.getRecipient());
            if (!recipientUsable(channel, recipient)) {
                updateDelivery(delivery, ChannelResult.failed("接收标识不可解析: " + delivery.getRecipient()));
                continue;
            }
            updateDelivery(delivery, sendGuarded(sender, record, payload, channel, recipient));
        }
        List<MessageDeliveryPo> deliveries = selectDeliveries(record.getId());
        record.setStatus(aggregateStatus(deliveries).name());
        recordMapper.updateById(record);
        log.info("消息重试完成: messageId={}, retried={}, status={}",
                record.getId(), failed.size(), record.getStatus());
        return buildResult(record, false, deliveries);
    }

    /**
     * 按幂等键查询既有消息。
     *
     * <p>租户隔离由 MyBatis-Plus 租户插件追加 {@code tenant_id = ?}（调用方已先设置
     * {@link com.bluewhale.common.tenant.TenantContext}，且 {@code msg_record} 不在插件的忽略清单内），
     * 唯一索引 {@code uk_tenant_idempotency (tenant_id, idempotency_key)} 与查询口径一致。</p>
     *
     * @param idempotencyKey 幂等键
     * @return 既有消息；不存在时为 {@code null}
     */
    private MessageRecordPo selectByIdempotencyKey(String idempotencyKey) {
        LambdaQueryWrapper<MessageRecordPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageRecordPo::getIdempotencyKey, idempotencyKey);
        return recordMapper.selectOne(wrapper);
    }

    /**
     * 解析模板：当前租户优先，业务租户未配置时回落到平台租户共享模板。
     *
     * @param templateCode 模板编码（可为空）
     * @return 模板；未使用模板时为 {@code null}
     */
    private MessageTemplatePo resolveTemplate(String templateCode) {
        if (!StringUtils.hasText(templateCode)) {
            return null;
        }
        String code = templateCode.trim().toUpperCase(Locale.ROOT);
        MessageTemplatePo template = selectTemplate(code);
        Long tenantId = TenantContext.get();
        if (template == null && tenantId != null && tenantId != TenantContext.DEFAULT_TENANT_ID) {
            TenantContext.set(TenantContext.DEFAULT_TENANT_ID);
            try {
                template = selectTemplate(code);
            } finally {
                TenantContext.set(tenantId);
            }
        }
        if (template == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "消息模板不存在: " + code);
        }
        return template;
    }

    /**
     * 在当前租户上下文中按编码查询启用模板。
     *
     * @param templateCode 模板编码
     * @return 模板；不存在时为 {@code null}
     */
    private MessageTemplatePo selectTemplate(String templateCode) {
        LambdaQueryWrapper<MessageTemplatePo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageTemplatePo::getTemplateCode, templateCode)
                .eq(MessageTemplatePo::getStatus, TEMPLATE_ENABLED);
        return templateMapper.selectOne(wrapper);
    }

    /**
     * 构造消息记录。
     *
     * @param request  发送请求
     * @param template 模板（可为 {@code null}）
     * @return 消息记录
     */
    private MessageRecordPo buildRecord(SendMessageRequest request, MessageTemplatePo template) {
        MessageRecordPo record = new MessageRecordPo();
        record.setBizType(request.getBizType());
        record.setBizId(request.getBizId());
        record.setTemplateCode(request.getTemplateCode());
        record.setIdempotencyKey(request.getIdempotencyKey());
        String title = resolveTitle(request, template);
        String content = resolveContent(request, template);
        // 模板渲染结果也可能为空，落库前拦截，避免投递空消息
        if (!StringUtils.hasText(title) && !StringUtils.hasText(content)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "消息标题与正文不能同时为空");
        }
        record.setTitle(title);
        record.setContent(content);
        record.setPayloadJson(toJson(request.getPayload()));
        record.setChannels(normalizeChannels(request.getChannels()));
        record.setStatus(MessageStatus.PENDING.name());
        CallbackUrlGuard.assertAllowed(request.getCallbackUrl());
        record.setCallbackUrl(request.getCallbackUrl());
        return record;
    }

    /**
     * 解析标题：请求显式标题优先，其次模板标题。
     *
     * @param request  发送请求
     * @param template 模板
     * @return 标题
     */
    private static String resolveTitle(SendMessageRequest request, MessageTemplatePo template) {
        if (StringUtils.hasText(request.getTitle())) {
            return request.getTitle();
        }
        if (template != null) {
            return TemplateRenderer.render(template.getTitleTemplate(), request.getPayload());
        }
        return "";
    }

    /**
     * 解析正文：请求显式正文优先，其次模板正文。
     *
     * @param request  发送请求
     * @param template 模板
     * @return 正文
     */
    private static String resolveContent(SendMessageRequest request, MessageTemplatePo template) {
        if (StringUtils.hasText(request.getContent())) {
            return request.getContent();
        }
        if (template != null) {
            return TemplateRenderer.render(template.getContentTemplate(), request.getPayload());
        }
        return "";
    }

    /**
     * 序列化模板变量。
     *
     * @param payload 变量
     * @return JSON；空变量返回 {@code "{}"}
     */
    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException e) {
            log.warn("消息模板变量序列化失败: reason={}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 规范化渠道列表：大写、去重、忽略非法值。
     *
     * @param channels 原始渠道
     * @return 逗号分隔的渠道
     */
    private static String normalizeChannels(List<String> channels) {
        Set<String> normalized = new LinkedHashSet<>();
        if (channels != null) {
            for (String raw : channels) {
                MessageChannel channel = MessageChannel.parse(raw);
                if (channel == null) {
                    log.warn("忽略无法识别的消息渠道: raw={}", raw);
                    continue;
                }
                normalized.add(channel.name());
            }
        }
        if (normalized.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "没有可识别的消息渠道");
        }
        return String.join(",", normalized);
    }

    /**
     * 逐渠道、逐接收人投递并落投递记录。
     *
     * @param record  消息记录
     * @param request 发送请求
     * @return 投递记录
     */
    private List<MessageDeliveryPo> dispatch(MessageRecordPo record, SendMessageRequest request) {
        Map<MessageChannel, MessageChannelSender> senderMap = senderMap();
        // 接收人去重：同一 (userId, email, phone) 只投递一次，与投递唯一索引
        // uk_message_channel_recipient 对齐，避免重复接收人撞键
        List<MessageRecipient> recipients = dedupeRecipients(request.getRecipients());
        Set<MessageChannel> channels = new LinkedHashSet<>();
        for (String raw : request.getChannels()) {
            MessageChannel channel = MessageChannel.parse(raw);
            if (channel == null) {
                log.warn("忽略无法识别的消息渠道: messageId={}, raw={}", record.getId(), raw);
                continue;
            }
            channels.add(channel);
        }
        List<MessageDeliveryPo> deliveries = new ArrayList<>();
        for (MessageChannel channel : channels) {
            MessageChannelSender sender = senderMap.get(channel);
            for (MessageRecipient recipient : recipients) {
                ChannelResult result;
                if (sender == null) {
                    result = ChannelResult.skipped("渠道未实现: " + channel.name());
                } else {
                    result = sendGuarded(sender, record, request.getPayload(), channel, recipient);
                }
                deliveries.add(insertDelivery(record, channel, recipient, result));
            }
        }
        return deliveries;
    }

    /**
     * 接收人去重：按 (userId, email, phone) 组合键去重，保持原有顺序。
     *
     * @param recipients 原始接收人列表
     * @return 去重后的接收人列表
     */
    private static List<MessageRecipient> dedupeRecipients(List<MessageRecipient> recipients) {
        if (recipients == null || recipients.size() <= 1) {
            return recipients;
        }
        Set<String> seen = new LinkedHashSet<>();
        return recipients.stream()
                .filter(recipient -> seen.add(recipientKey(recipient)))
                .toList();
    }

    /**
     * 构造接收人去重键。
     *
     * @param recipient 接收人
     * @return 组合键
     */
    private static String recipientKey(MessageRecipient recipient) {
        if (recipient == null) {
            return "-";
        }
        return recipient.getUserId() + "|" + recipient.getEmail() + "|" + recipient.getPhone();
    }

    /**
     * 收集已注册的渠道适配器（同渠道重复注册时保留第一个）。
     *
     * <p>Bean 生命周期内注册表不变，首次调用后缓存；懒初始化而非 {@code @PostConstruct}，
     * 保证非容器构造（如单元测试）同样可用。</p>
     *
     * @return 渠道到适配器的映射
     */
    private Map<MessageChannel, MessageChannelSender> senderMap() {
        Map<MessageChannel, MessageChannelSender> cache = senderCache;
        if (cache == null) {
            cache = senders.stream()
                    .collect(Collectors.toMap(MessageChannelSender::channel, sender -> sender,
                            (first, second) -> first));
            senderCache = cache;
        }
        return cache;
    }

    /**
     * 调用渠道适配器并隔离异常。
     *
     * @param sender    渠道适配器
     * @param record    消息记录
     * @param payload   模板变量
     * @param channel   渠道
     * @param recipient 接收人
     * @return 发送结果
     */
    private ChannelResult safeSend(MessageChannelSender sender, MessageRecordPo record,
                                   Map<String, Object> payload, MessageChannel channel,
                                   MessageRecipient recipient) {
        try {
            return sender.send(ChannelMessage.builder()
                    .messageId(record.getId())
                    .tenantId(TenantContext.get())
                    .bizType(record.getBizType())
                    .bizId(record.getBizId())
                    .title(record.getTitle())
                    .content(record.getContent())
                    .payload(payload)
                    .recipient(recipient)
                    .build());
        } catch (Exception e) {
            log.error("消息渠道调用异常: messageId={}, channel={}, reason={}",
                    record.getId(), channel, e.getMessage(), e);
            return ChannelResult.failed("渠道调用异常: " + e.getMessage());
        }
    }

    /**
     * 经渠道保护（限流/熔断）后调用适配器。
     *
     * <p>被拦截时记 {@code SKIPPED} 并说明原因，业务方按幂等键重试即可补发；
     * 真实调用结果回写熔断计数。</p>
     *
     * @param sender    渠道适配器
     * @param record    消息记录
     * @param payload   模板变量
     * @param channel   渠道
     * @param recipient 接收人
     * @return 发送结果
     */
    private ChannelResult sendGuarded(MessageChannelSender sender, MessageRecordPo record,
                                      Map<String, Object> payload, MessageChannel channel,
                                      MessageRecipient recipient) {
        MessageChannelProperties.Guard guard = guardOf(channel);
        ChannelGuard.Decision decision = channelGuard.acquire(channel, guard);
        if (!decision.allowed()) {
            log.warn("消息渠道受保护，跳过投递: messageId={}, channel={}, reason={}",
                    record.getId(), channel, decision.reason());
            metrics.recordSend(channel, DeliveryStatus.SKIPPED, 0L);
            return ChannelResult.skipped(decision.reason());
        }
        long startNanos = System.nanoTime();
        ChannelResult result = safeSend(sender, record, payload, channel, recipient);
        channelGuard.record(channel, guard, result.getStatus());
        metrics.recordSend(channel, result.getStatus(), System.nanoTime() - startNanos);
        return result;
    }

    /**
     * 取渠道的限流/熔断配置；站内信为本地落库，无需保护。
     *
     * @param channel 渠道
     * @return 保护配置；无需保护时为 {@code null}
     */
    private MessageChannelProperties.Guard guardOf(MessageChannel channel) {
        return switch (channel) {
            case EMAIL -> properties.getEmail().getGuard();
            case SMS -> properties.getSms().getGuard();
            case IM -> properties.getIm().getGuard();
            case INAPP -> null;
        };
    }

    /**
     * 落投递记录。
     *
     * @param record    消息记录
     * @param channel   渠道
     * @param recipient 接收人
     * @param result    发送结果
     * @return 投递记录
     */
    private MessageDeliveryPo insertDelivery(MessageRecordPo record, MessageChannel channel,
                                             MessageRecipient recipient, ChannelResult result) {
        MessageDeliveryPo delivery = new MessageDeliveryPo();
        delivery.setMessageId(record.getId());
        delivery.setChannel(channel.name());
        delivery.setRecipient(resolveRecipient(channel, recipient));
        delivery.setStatus(result.getStatus().name());
        delivery.setProviderMessageId(truncate(result.getProviderMessageId(), PROVIDER_MESSAGE_ID_MAX_LENGTH));
        delivery.setErrorMessage(truncate(result.getErrorMessage(), 512));
        delivery.setAttempts(1);
        delivery.setSentAt(LocalDateTime.now());
        deliveryMapper.insert(delivery);
        return delivery;
    }

    /**
     * 解析投递接收标识：按渠道取用户ID/邮箱/手机号。
     *
     * @param channel   渠道
     * @param recipient 接收人
     * @return 接收标识
     */
    private static String resolveRecipient(MessageChannel channel, MessageRecipient recipient) {
        if (recipient == null) {
            return "-";
        }
        return switch (channel) {
            case INAPP, IM -> recipient.getUserId() == null ? "-" : recipient.getUserId().toString();
            case EMAIL -> StringUtils.hasText(recipient.getEmail()) ? recipient.getEmail() : "-";
            case SMS -> StringUtils.hasText(recipient.getPhone()) ? recipient.getPhone() : "-";
        };
    }

    /**
     * 聚合消息状态。
     *
     * @param deliveries 投递记录
     * @return 消息状态
     */
    private static MessageStatus aggregateStatus(List<MessageDeliveryPo> deliveries) {
        return MessageStatusAggregator.aggregate(deliveries);
    }

    /**
     * 构造发送结果。
     *
     * @param record 消息记录
     * @param duplicate 是否幂等命中
     * @return 发送结果
     */
    private SendMessageResult buildResult(MessageRecordPo record, boolean duplicate) {
        return buildResult(record, duplicate, selectDeliveries(record.getId()));
    }

    /**
     * 查询一条消息的全部投递记录。
     *
     * @param messageId 消息ID
     * @return 按投递记录ID升序的投递记录
     */
    private List<MessageDeliveryPo> selectDeliveries(Long messageId) {
        LambdaQueryWrapper<MessageDeliveryPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageDeliveryPo::getMessageId, messageId)
                .orderByAsc(MessageDeliveryPo::getId);
        return deliveryMapper.selectList(wrapper);
    }

    /**
     * 解析消息变量 JSON。
     *
     * @param payloadJson 变量 JSON
     * @return 变量映射；解析失败时返回空映射
     */
    private Map<String, Object> parsePayload(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("消息变量解析失败: reason={}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 从投递记录中的接收标识重建接收人。
     *
     * @param channel   渠道
     * @param recipient 接收标识
     * @return 接收人
     */
    private static MessageRecipient rebuildRecipient(MessageChannel channel, String recipient) {
        MessageRecipient result = new MessageRecipient();
        if (!StringUtils.hasText(recipient) || "-".equals(recipient)) {
            return result;
        }
        switch (channel) {
            case INAPP, IM -> {
                try {
                    result.setUserId(Long.valueOf(recipient));
                } catch (NumberFormatException e) {
                    result.setUserId(null);
                }
            }
            case EMAIL -> result.setEmail(recipient);
            case SMS -> result.setPhone(recipient);
        }
        return result;
    }

    /**
     * 判断接收人是否具备当前渠道所需的信息。
     *
     * @param channel   渠道
     * @param recipient 接收人
     * @return 可用于投递时返回 {@code true}
     */
    private static boolean recipientUsable(MessageChannel channel, MessageRecipient recipient) {
        return switch (channel) {
            case INAPP, IM -> recipient.getUserId() != null;
            case EMAIL -> StringUtils.hasText(recipient.getEmail());
            case SMS -> StringUtils.hasText(recipient.getPhone());
        };
    }

    /**
     * 用重试结果回写原投递记录。
     *
     * @param delivery 投递记录
     * @param result   重试结果
     */
    private void updateDelivery(MessageDeliveryPo delivery, ChannelResult result) {
        delivery.setStatus(result.getStatus().name());
        // 重试失败/跳过时结果不含新标识，不能把首次发送已绑定的标识清空
        if (StringUtils.hasText(result.getProviderMessageId())) {
            delivery.setProviderMessageId(truncate(result.getProviderMessageId(), PROVIDER_MESSAGE_ID_MAX_LENGTH));
        }
        delivery.setErrorMessage(truncate(result.getErrorMessage(), 512));
        delivery.setAttempts(delivery.getAttempts() == null ? 2 : delivery.getAttempts() + 1);
        delivery.setSentAt(LocalDateTime.now());
        deliveryMapper.updateById(delivery);
    }

    /**
     * 构造发送结果。
     *
     * @param record 消息记录
     * @param duplicate 是否幂等命中
     * @param deliveries 投递记录
     * @return 发送结果
     */
    private static SendMessageResult buildResult(MessageRecordPo record, boolean duplicate,
                                                 List<MessageDeliveryPo> deliveries) {
        SendMessageResult result = new SendMessageResult();
        result.setMessageId(record.getId());
        result.setDuplicate(duplicate);
        result.setStatus(record.getStatus());
        result.setDeliveries(deliveries.stream().map(MessageSendServiceImpl::toDeliveryVo).toList());
        return result;
    }

    /**
     * 投递记录转视图。
     *
     * @param delivery 投递记录
     * @return 视图
     */
    private static MessageDeliveryVO toDeliveryVo(MessageDeliveryPo delivery) {
        return MessageConverter.INSTANCE.toDeliveryVo(delivery);
    }

    /**
     * 截断文本。
     *
     * @param value 原值
     * @param max   最大长度
     * @return 截断值
     */
    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
