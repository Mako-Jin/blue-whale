package com.bluewhale.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.message.config.MessageReceiptProperties;
import com.bluewhale.message.dto.DeliveryReceiptItem;
import com.bluewhale.message.dto.DeliveryReceiptRequest;
import com.bluewhale.message.entity.MessageCallbackTask;
import com.bluewhale.message.entity.MessageDelivery;
import com.bluewhale.message.entity.MessageReceipt;
import com.bluewhale.message.entity.MessageRecord;
import com.bluewhale.message.enums.DeliveryStatus;
import com.bluewhale.message.enums.MessageChannel;
import com.bluewhale.message.enums.MessageStatus;
import com.bluewhale.message.mapper.MessageCallbackTaskMapper;
import com.bluewhale.message.mapper.MessageDeliveryMapper;
import com.bluewhale.message.mapper.MessageReceiptMapper;
import com.bluewhale.message.mapper.MessageRecordMapper;
import com.bluewhale.message.service.MessageReceiptService;
import com.bluewhale.message.vo.ReceiptApplyResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 渠道回执受理实现。
 *
 * <p>三条硬约束：</p>
 * <ol>
 *   <li><b>幂等</b>：{@code (tenant_id, receipt_key)} 唯一，重复上报一律计为 duplicated，
 *       不重复推进状态、不重复排队回调；并发插入撞键同样按幂等处理。</li>
 *   <li><b>单向推进</b>：回执只会让投递状态向前走（见 {@link DeliveryStatus#canAdvanceTo}），
 *       乱序到达的"已送达"不会把"已读"打回。</li>
 *   <li><b>不伪造</b>：未匹配到投递记录的回执照常落库并标记 UNMATCHED，供运维对账，
 *       但绝不凭空创建投递记录。</li>
 * </ol>
 *
 * <p><b>匹配口径</b>：回执只按 {@code (channel, msg_delivery.provider_message_id)} 精确匹配。
 * 因此渠道发送时必须把渠道侧消息标识回写为 {@code providerMessageId}，否则回执必然 UNMATCHED：
 * 站内信以平台消息ID充当该标识，可直接匹配；EMAIL / SMS / IM 在 M0 未从供应商响应中解析消息ID，
 * 其回执依赖后续按供应商协议补齐标识，期间一律落 UNMATCHED 等待对账，不做模糊匹配
 * （按接收人+时间窗兜底可能把回执错误归到同接收人的其它消息上）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageReceiptServiceImpl implements MessageReceiptService {

    /** 回执匹配成功 */
    private static final String MATCH_MATCHED = "MATCHED";

    /** 回执未匹配到投递记录 */
    private static final String MATCH_UNMATCHED = "UNMATCHED";

    /** 回执幂等键最大长度（列宽 160） */
    private static final int RECEIPT_KEY_MAX_LENGTH = 160;

    /** 错误说明最大长度（列宽 512） */
    private static final int ERROR_MAX_LENGTH = 512;

    /** 原始报文最大长度 */
    private static final int RAW_MAX_LENGTH = 4000;

    /** 已读标记值 */
    private static final int READ_FLAG_READ = 1;

    /** 回调任务初始状态 */
    private static final String TASK_PENDING = "PENDING";

    private final MessageRecordMapper recordMapper;

    private final MessageDeliveryMapper deliveryMapper;

    private final MessageReceiptMapper receiptMapper;

    private final MessageCallbackTaskMapper callbackTaskMapper;

    private final ObjectMapper objectMapper;

    private final MessageReceiptProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReceiptApplyResult apply(DeliveryReceiptRequest request) {
        if (!properties.isEnabled()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "回执受理未启用（whale.message.receipt.enabled=false）");
        }
        Long tenantId = request.getTenantId() == null ? TenantContext.get() : request.getTenantId();
        if (tenantId == null || tenantId <= 0L) {
            throw new BizException(ErrorCode.PARAM_ERROR, "缺少租户ID");
        }
        Long previous = TenantContext.get();
        TenantContext.set(tenantId);
        try {
            ReceiptApplyResult result = new ReceiptApplyResult();
            result.setReceived(request.getReceipts().size());
            Set<Long> touchedMessages = new LinkedHashSet<>();
            for (DeliveryReceiptItem item : request.getReceipts()) {
                handleOne(item, result, touchedMessages);
            }
            touchedMessages.forEach(this::refreshRecordStatus);
            log.info("回执受理完成: tenantId={}, received={}, accepted={}, duplicated={}, unmatched={}, advanced={}, queued={}",
                    tenantId, result.getReceived(), result.getAccepted(), result.getDuplicated(),
                    result.getUnmatched(), result.getAdvanced(), result.getCallbackQueued());
            return result;
        } finally {
            TenantContext.set(previous);
        }
    }

    /**
     * 处理单条回执。
     *
     * @param item            回执明细
     * @param result          累计结果
     * @param touchedMessages 本次推进过状态的逻辑消息ID集合
     */
    private void handleOne(DeliveryReceiptItem item, ReceiptApplyResult result, Set<Long> touchedMessages) {
        DeliveryStatus target = DeliveryStatus.parse(item.getStatus());
        if (target == null || !target.isReceiptStatus()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "回执状态非法（仅支持 DELIVERED / READ / BOUNCED）: "
                    + item.getStatus());
        }
        String channel = item.getChannel().trim().toUpperCase(Locale.ROOT);
        if (MessageChannel.parse(channel) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "无法识别的回执渠道: " + item.getChannel());
        }
        LocalDateTime receiptTime = item.getReceiptTime() == null ? LocalDateTime.now() : item.getReceiptTime();
        String receiptKey = truncate(buildReceiptKey(channel, item, target, receiptTime), RECEIPT_KEY_MAX_LENGTH);
        if (existsReceipt(receiptKey)) {
            result.setDuplicated(result.getDuplicated() + 1);
            return;
        }
        List<MessageDelivery> deliveries = matchDeliveries(channel, item.getProviderMessageId());
        MessageReceipt receipt = buildReceipt(channel, item, target, receiptTime, receiptKey, deliveries);
        try {
            receiptMapper.insert(receipt);
        } catch (DuplicateKeyException e) {
            log.info("回执幂等命中（并发）: receiptKey={}", receiptKey);
            result.setDuplicated(result.getDuplicated() + 1);
            return;
        }
        result.setAccepted(result.getAccepted() + 1);
        if (deliveries.isEmpty()) {
            result.setUnmatched(result.getUnmatched() + 1);
            log.warn("回执未匹配到投递记录: channel={}, providerMessageId={}",
                    channel, item.getProviderMessageId());
            return;
        }
        for (MessageDelivery delivery : deliveries) {
            if (!applyTransition(delivery, target, receiptTime, item.getErrorMessage())) {
                continue;
            }
            result.setAdvanced(result.getAdvanced() + 1);
            touchedMessages.add(delivery.getMessageId());
            enqueueCallback(receipt, delivery, target, receiptTime, item, result);
        }
    }

    /**
     * 构造回执幂等键：优先渠道回执ID，缺省时按渠道+提供方消息ID+状态+回执时间派生。
     *
     * @param channel     渠道
     * @param item        回执明细
     * @param target      回执状态
     * @param receiptTime 回执时间
     * @return 幂等键
     */
    private static String buildReceiptKey(String channel, DeliveryReceiptItem item,
                                          DeliveryStatus target, LocalDateTime receiptTime) {
        if (StringUtils.hasText(item.getReceiptId())) {
            return channel + ":" + item.getReceiptId().trim();
        }
        return channel + ":" + item.getProviderMessageId() + ":" + target.name() + ":" + receiptTime;
    }

    /**
     * 判断回执是否已受理。
     *
     * @param receiptKey 幂等键
     * @return 已存在返回 {@code true}
     */
    private boolean existsReceipt(String receiptKey) {
        LambdaQueryWrapper<MessageReceipt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageReceipt::getReceiptKey, receiptKey);
        return receiptMapper.selectCount(wrapper) > 0L;
    }

    /**
     * 按渠道 + 渠道侧消息标识匹配投递记录。
     *
     * @param channel           渠道
     * @param providerMessageId 渠道侧消息标识
     * @return 匹配到的投递记录（可能多条，如站内信一条消息多个接收人）
     */
    private List<MessageDelivery> matchDeliveries(String channel, String providerMessageId) {
        if (!StringUtils.hasText(providerMessageId)) {
            return List.of();
        }
        LambdaQueryWrapper<MessageDelivery> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageDelivery::getChannel, channel)
                .eq(MessageDelivery::getProviderMessageId, providerMessageId.trim())
                .orderByAsc(MessageDelivery::getId);
        return deliveryMapper.selectList(wrapper);
    }

    /**
     * 构造回执流水记录。
     *
     * @param channel     渠道
     * @param item        回执明细
     * @param target      回执状态
     * @param receiptTime 回执时间
     * @param receiptKey  幂等键
     * @param deliveries  匹配到的投递记录
     * @return 回执流水
     */
    private MessageReceipt buildReceipt(String channel, DeliveryReceiptItem item, DeliveryStatus target,
                                        LocalDateTime receiptTime, String receiptKey,
                                        List<MessageDelivery> deliveries) {
        MessageReceipt receipt = new MessageReceipt();
        receipt.setReceiptKey(receiptKey);
        receipt.setChannel(channel);
        receipt.setProviderMessageId(truncate(item.getProviderMessageId(), 128));
        receipt.setReceiptStatus(target.name());
        receipt.setReceiptTime(receiptTime);
        receipt.setErrorCode(truncate(item.getErrorCode(), 64));
        receipt.setErrorMessage(truncate(item.getErrorMessage(), ERROR_MAX_LENGTH));
        receipt.setRawPayload(truncate(item.getRawPayload(), RAW_MAX_LENGTH));
        if (deliveries.isEmpty()) {
            receipt.setMatchStatus(MATCH_UNMATCHED);
            return receipt;
        }
        MessageDelivery first = deliveries.get(0);
        receipt.setMatchStatus(MATCH_MATCHED);
        receipt.setDeliveryId(first.getId());
        receipt.setMessageId(first.getMessageId());
        return receipt;
    }

    /**
     * 推进投递状态（单向）。
     *
     * @param delivery     投递记录
     * @param target       目标回执态
     * @param receiptTime  回执时间
     * @param errorMessage 退信原因
     * @return 实际推进返回 {@code true}
     */
    private boolean applyTransition(MessageDelivery delivery, DeliveryStatus target,
                                    LocalDateTime receiptTime, String errorMessage) {
        DeliveryStatus current = DeliveryStatus.parse(delivery.getStatus());
        if (current == null || !current.canAdvanceTo(target)) {
            log.debug("回执未推进投递状态: deliveryId={}, current={}, target={}",
                    delivery.getId(), delivery.getStatus(), target);
            return false;
        }
        delivery.setStatus(target.name());
        if (target == DeliveryStatus.DELIVERED) {
            delivery.setDeliveredAt(receiptTime);
        } else if (target == DeliveryStatus.READ) {
            delivery.setDeliveredAt(delivery.getDeliveredAt() == null ? receiptTime : delivery.getDeliveredAt());
            delivery.setReadAt(receiptTime);
            delivery.setReadFlag(READ_FLAG_READ);
        } else if (target == DeliveryStatus.BOUNCED) {
            delivery.setErrorMessage(truncate(errorMessage == null ? "渠道退信" : errorMessage, ERROR_MAX_LENGTH));
        }
        deliveryMapper.updateById(delivery);
        return true;
    }

    /**
     * 投递回调任务（outbox），业务方未登记回调地址时跳过。
     *
     * @param receipt     回执流水
     * @param delivery    投递记录
     * @param target      回执状态
     * @param receiptTime 回执时间
     * @param item        回执明细
     * @param result      累计结果
     */
    private void enqueueCallback(MessageReceipt receipt, MessageDelivery delivery, DeliveryStatus target,
                                 LocalDateTime receiptTime, DeliveryReceiptItem item, ReceiptApplyResult result) {
        MessageRecord record = delivery.getMessageId() == null ? null : recordMapper.selectById(delivery.getMessageId());
        if (record == null || !StringUtils.hasText(record.getCallbackUrl())) {
            return;
        }
        MessageCallbackTask task = new MessageCallbackTask();
        task.setMessageId(delivery.getMessageId());
        task.setDeliveryId(delivery.getId());
        task.setReceiptId(receipt.getId());
        task.setCallbackUrl(truncate(record.getCallbackUrl(), 512));
        task.setPayloadJson(toJson(buildCallbackPayload(receipt, delivery, record, target, receiptTime, item)));
        task.setStatus(TASK_PENDING);
        task.setAttempts(0);
        task.setNextRetryAt(LocalDateTime.now());
        try {
            callbackTaskMapper.insert(task);
            result.setCallbackQueued(result.getCallbackQueued() + 1);
        } catch (DuplicateKeyException e) {
            log.debug("回调任务已存在: receiptId={}, deliveryId={}", receipt.getId(), delivery.getId());
        }
    }

    /**
     * 构造回调报文：同时带平台侧ID与业务侧标识，业务方按需取用。
     *
     * @param receipt     回执流水
     * @param delivery    投递记录
     * @param record      逻辑消息
     * @param target      回执状态
     * @param receiptTime 回执时间
     * @param item        回执明细
     * @return 报文映射
     */
    private static Map<String, Object> buildCallbackPayload(MessageReceipt receipt, MessageDelivery delivery,
                                                            MessageRecord record, DeliveryStatus target,
                                                            LocalDateTime receiptTime, DeliveryReceiptItem item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", delivery.getMessageId());
        payload.put("deliveryId", delivery.getId());
        payload.put("receiptId", receipt.getId());
        payload.put("bizType", record.getBizType());
        payload.put("bizId", record.getBizId());
        payload.put("channel", delivery.getChannel());
        payload.put("recipient", delivery.getRecipient());
        payload.put("status", target.name());
        payload.put("providerMessageId", delivery.getProviderMessageId());
        // 用 ISO 文本承载时间，避免依赖调用方 Jackson 是否注册 JavaTimeModule
        payload.put("receiptTime", receiptTime == null ? null : receiptTime.toString());
        payload.put("errorCode", item.getErrorCode());
        payload.put("errorMessage", item.getErrorMessage());
        return payload;
    }

    /**
     * 按回执结果重算逻辑消息状态：无待送达、无失败、无退信且至少一条送达时置为 DELIVERED。
     *
     * @param messageId 消息ID
     */
    private void refreshRecordStatus(Long messageId) {
        MessageRecord record = messageId == null ? null : recordMapper.selectById(messageId);
        if (record == null) {
            return;
        }
        LambdaQueryWrapper<MessageDelivery> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageDelivery::getMessageId, messageId);
        List<MessageDelivery> deliveries = deliveryMapper.selectList(wrapper);
        long delivered = 0L;
        long pending = 0L;
        long abnormal = 0L;
        for (MessageDelivery delivery : deliveries) {
            DeliveryStatus status = DeliveryStatus.parse(delivery.getStatus());
            if (status == DeliveryStatus.DELIVERED || status == DeliveryStatus.READ) {
                delivered++;
            } else if (status == DeliveryStatus.SENT) {
                pending++;
            } else if (status == DeliveryStatus.FAILED || status == DeliveryStatus.BOUNCED) {
                abnormal++;
            }
        }
        if (delivered == 0L || pending > 0L) {
            return;
        }
        String next = abnormal == 0L ? MessageStatus.DELIVERED.name() : MessageStatus.PARTIAL.name();
        if (next.equals(record.getStatus())) {
            return;
        }
        record.setStatus(next);
        recordMapper.updateById(record);
    }

    /**
     * 序列化回调报文。
     *
     * @param payload 报文映射
     * @return JSON；序列化失败时返回空报文
     */
    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("回执回调报文序列化失败: reason={}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 截断文本到列宽上限。
     *
     * @param value 原值
     * @param max   最大长度
     * @return 截断后的值
     */
    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
