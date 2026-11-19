package com.bluewhale.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.message.channel.MessageChannelSender;
import com.bluewhale.message.config.MessageReceiptProperties;
import com.bluewhale.message.dto.DeliveryReceiptItem;
import com.bluewhale.message.dto.DeliveryReceiptRequest;
import com.bluewhale.message.po.MessageCallbackTaskPo;
import com.bluewhale.message.po.MessageDeliveryPo;
import com.bluewhale.message.po.MessageReceiptPo;
import com.bluewhale.message.po.MessageRecordPo;
import com.bluewhale.message.enums.DeliveryStatus;
import com.bluewhale.message.enums.MessageChannel;
import com.bluewhale.message.mapper.MessageCallbackTaskMapper;
import com.bluewhale.message.mapper.MessageDeliveryMapper;
import com.bluewhale.message.mapper.MessageReceiptMapper;
import com.bluewhale.message.mapper.MessageRecordMapper;
import com.bluewhale.message.service.MessageReceiptService;
import com.bluewhale.message.support.MessageMetrics;
import com.bluewhale.message.support.MessageStatusAggregator;
import com.bluewhale.message.support.ReceiptMatcher;
import com.bluewhale.message.vo.ReceiptApplyResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
 * <p><b>匹配口径</b>：按 {@link ReceiptMatcher} 策略链依次匹配——优先
 * {@code (channel, msg_delivery.provider_message_id)} 精确命中，未命中且回执携带
 * {@code recipient} 与 {@code receiptTime} 时，再由兜底策略按「渠道 + 接收人 + 发送时间窗」
 * 补齐（只认尚未绑定供应商标识的投递）。各渠道的标识来源：</p>
 * <ul>
 *   <li>EMAIL：发送时写入 RFC 5322 {@code Message-ID}，退信/已读回执原样携带；</li>
 *   <li>SMS：解析网关同步响应中的 {@code providerIdField}，异步回执携带同一标识；</li>
 *   <li>IM：投递幂等键 {@code clientMsgId}，由 whale-im 回执回传；</li>
 *   <li>INAPP：以平台消息ID充当该标识，可直接匹配。</li>
 * </ul>
 * <p>渠道未回传标识、且回执也未携带接收人与时间的，一律落 UNMATCHED 供对账；
 * 兜底匹配不回写 {@code provider_message_id}，避免后续精确回执被错误归并。</p>
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

    private final List<MessageChannelSender> senders;

    private final List<ReceiptMatcher> matchers;

    private final MessageMetrics metrics;

    /** 渠道能力缓存：Bean 生命周期内注册表不变，首次使用时构建一次 */
    private volatile Map<MessageChannel, MessageChannelSender> senderCache;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReceiptApplyResult apply(DeliveryReceiptRequest request) {
        if (!properties.isEnabled()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "回执受理未启用（whale.message.receipt.enabled=false）");
        }
        // 内网回执必须显式传入 tenantId：不再从上下文兜底，避免网关漏传租户头时串租户受理
        Long tenantId = request.getTenantId();
        if (tenantId == null || tenantId <= 0L) {
            throw new BizException(ErrorCode.PARAM_ERROR, "缺少租户ID（内网回执必须显式传入 tenantId）");
        }
        Long previous = TenantContext.get();
        TenantContext.set(tenantId);
        try {
            ReceiptApplyResult result = new ReceiptApplyResult();
            result.setReceived(request.getReceipts().size());
            // 先整批校验并构造幂等键，再批量预取去重键与投递记录，避免逐条查库
            List<PreparedReceipt> prepared = request.getReceipts().stream()
                    .map(this::prepare)
                    .toList();
            Set<String> existingKeys = existedReceiptKeys(prepared);
            Map<DeliveryReceiptItem, List<MessageDeliveryPo>> matches = matchDeliveries(prepared);
            Set<Long> touchedMessages = new LinkedHashSet<>();
            Map<Long, MessageRecordPo> recordCache = new HashMap<>();
            for (PreparedReceipt item : prepared) {
                handleOne(item, existingKeys, matches, result, touchedMessages, recordCache);
            }
            refreshRecordStatuses(touchedMessages, recordCache);
            log.info("回执受理完成: tenantId={}, received={}, accepted={}, duplicated={}, unmatched={}, advanced={}, queued={}",
                    tenantId, result.getReceived(), result.getAccepted(), result.getDuplicated(),
                    result.getUnmatched(), result.getAdvanced(), result.getCallbackQueued());
            return result;
        } finally {
            TenantContext.set(previous);
        }
    }

    /**
     * 校验单条回执并预计算幂等键。
     *
     * <p>除状态/渠道合法性外，还按渠道适配器的能力声明校验：未实现回执的渠道、
     * 不支持已读却上报 {@code READ} 的渠道一律拒绝，避免脏数据进入投递与回调链路。</p>
     *
     * @param item 回执明细
     * @return 预处理结果
     */
    private PreparedReceipt prepare(DeliveryReceiptItem item) {
        DeliveryStatus target = DeliveryStatus.parse(item.getStatus());
        if (target == null || !target.isReceiptStatus()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "回执状态非法（仅支持 DELIVERED / READ / BOUNCED）: "
                    + item.getStatus());
        }
        String channel = item.getChannel().trim().toUpperCase(Locale.ROOT);
        MessageChannel parsedChannel = MessageChannel.parse(channel);
        if (parsedChannel == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "无法识别的回执渠道: " + item.getChannel());
        }
        MessageChannelSender sender = senderMap().get(parsedChannel);
        if (sender == null || !sender.supportsReceipt()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "渠道不支持回执上报: " + channel);
        }
        if (target == DeliveryStatus.READ && !sender.supportsReadReceipt()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "渠道不支持已读回执: " + channel);
        }
        LocalDateTime receiptTime = item.getReceiptTime() == null ? LocalDateTime.now() : item.getReceiptTime();
        String receiptKey = truncate(buildReceiptKey(channel, item, target, receiptTime), RECEIPT_KEY_MAX_LENGTH);
        return new PreparedReceipt(item, channel, target, receiptTime, receiptKey);
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
     * 处理单条已预处理的回执。
     *
     * @param prepared        预处理结果
     * @param existingKeys    库中已存在的幂等键（本批插入后会同步加入，保证批内幂等）
     * @param matches         本批回执到投递记录的匹配结果
     * @param result          累计结果
     * @param touchedMessages 本次推进过状态的逻辑消息ID集合
     * @param recordCache     逻辑消息缓存，避免同批重复查询
     */
    private void handleOne(PreparedReceipt prepared, Set<String> existingKeys,
                           Map<DeliveryReceiptItem, List<MessageDeliveryPo>> matches, ReceiptApplyResult result,
                           Set<Long> touchedMessages, Map<Long, MessageRecordPo> recordCache) {
        DeliveryReceiptItem item = prepared.item();
        String receiptKey = prepared.receiptKey();
        // 批内重复同样计为 duplicated：本批已插入的键会同步写入 existingKeys
        if (!existingKeys.add(receiptKey)) {
            result.setDuplicated(result.getDuplicated() + 1);
            return;
        }
        List<MessageDeliveryPo> deliveries = matches.getOrDefault(item, List.of());
        MessageReceiptPo receipt = buildReceipt(prepared.channel(), item, prepared.target(),
                prepared.receiptTime(), receiptKey, deliveries);
        try {
            receiptMapper.insert(receipt);
        } catch (DuplicateKeyException e) {
            log.info("回执幂等命中（并发）: receiptKey={}", receiptKey);
            result.setDuplicated(result.getDuplicated() + 1);
            return;
        }
        result.setAccepted(result.getAccepted() + 1);
        metrics.recordReceipt(prepared.channel(), prepared.target(), !deliveries.isEmpty());
        if (deliveries.isEmpty()) {
            result.setUnmatched(result.getUnmatched() + 1);
            log.warn("回执未匹配到投递记录: channel={}, providerMessageId={}",
                    prepared.channel(), item.getProviderMessageId());
            return;
        }
        for (MessageDeliveryPo delivery : deliveries) {
            if (!applyTransition(delivery, prepared.target(), prepared.receiptTime(), item.getErrorMessage())) {
                continue;
            }
            result.setAdvanced(result.getAdvanced() + 1);
            touchedMessages.add(delivery.getMessageId());
            enqueueCallback(receipt, delivery, prepared.target(), prepared.receiptTime(), item, result, recordCache);
        }
    }

    /**
     * 构造回执幂等键：优先渠道回执ID，缺省时按渠道+状态+（提供方消息ID+回执时间）摘要派生。
     *
     * <p>派生内容（providerMessageId 最长 128 + 时间戳 + 渠道/状态前缀）明文拼接会超过
     * {@code receipt_key VARCHAR(160)}，被截断后不同回执可能碰撞、后到回执被误判为重复而丢弃。
     * 因此对可变长部分取 SHA-256，键长固定且远小于列宽。</p>
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
        String digestSource = item.getProviderMessageId() + "|" + receiptTime;
        return channel + ":" + target.name() + ":" + sha256Hex(digestSource);
    }

    /**
     * 计算 SHA-256 十六进制摘要。
     *
     * @param source 原文
     * @return 64 位小写十六进制串
     */
    private static String sha256Hex(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // JDK 必须提供 SHA-256；走到这里说明运行环境被裁剪，属于不可恢复错误
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 批量查询本批幂等键中已存在的集合。
     *
     * @param prepared 预处理结果
     * @return 已存在的幂等键集合
     */
    private Set<String> existedReceiptKeys(List<PreparedReceipt> prepared) {
        Set<String> keys = prepared.stream()
                .map(PreparedReceipt::receiptKey)
                .collect(Collectors.toSet());
        if (keys.isEmpty()) {
            return new LinkedHashSet<>();
        }
        LambdaQueryWrapper<MessageReceiptPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(MessageReceiptPo::getReceiptKey, keys);
        return receiptMapper.selectList(wrapper).stream()
                .map(MessageReceiptPo::getReceiptKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 按策略链批量匹配投递记录：低优先级策略只处理前序未命中的回执。
     *
     * <p>租户隔离依赖租户插件追加 {@code tenant_id = ?}：受理入口已按回执请求切换
     * {@link com.bluewhale.common.tenant.TenantContext}，且 {@code msg_delivery} 不在插件忽略清单内，
     * 因此不会匹配到其它租户的投递记录。</p>
     *
     * @param prepared 预处理结果
     * @return 回执到投递记录的匹配结果
     */
    private Map<DeliveryReceiptItem, List<MessageDeliveryPo>> matchDeliveries(List<PreparedReceipt> prepared) {
        Map<DeliveryReceiptItem, List<MessageDeliveryPo>> matches = new HashMap<>();
        if (prepared.isEmpty() || matchers.isEmpty()) {
            return matches;
        }
        List<DeliveryReceiptItem> pending = prepared.stream().map(PreparedReceipt::item).toList();
        List<ReceiptMatcher> ordered = matchers.stream()
                .sorted(Comparator.comparingInt(ReceiptMatcher::order))
                .toList();
        for (ReceiptMatcher matcher : ordered) {
            List<DeliveryReceiptItem> applicable = pending.stream()
                    .filter(item -> {
                        MessageChannel channel = MessageChannel.parse(item.getChannel());
                        return channel != null && matcher.supports(channel);
                    })
                    .toList();
            if (applicable.isEmpty()) {
                continue;
            }
            matches.putAll(matcher.match(applicable));
            List<DeliveryReceiptItem> remaining = pending.stream()
                    .filter(item -> !matches.containsKey(item))
                    .toList();
            pending = remaining;
            if (pending.isEmpty()) {
                break;
            }
        }
        return matches;
    }

    /**
     * 回执预处理结果：校验后的状态、渠道、回执时间与幂等键。
     *
     * @param item        原始回执明细
     * @param channel     归一化渠道
     * @param target      回执状态
     * @param receiptTime 回执时间
     * @param receiptKey  幂等键
     */
    private record PreparedReceipt(DeliveryReceiptItem item, String channel, DeliveryStatus target,
                                   LocalDateTime receiptTime, String receiptKey) {
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
    private MessageReceiptPo buildReceipt(String channel, DeliveryReceiptItem item, DeliveryStatus target,
                                          LocalDateTime receiptTime, String receiptKey,
                                          List<MessageDeliveryPo> deliveries) {
        MessageReceiptPo receipt = new MessageReceiptPo();
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
        MessageDeliveryPo first = deliveries.get(0);
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
    private boolean applyTransition(MessageDeliveryPo delivery, DeliveryStatus target,
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
     * @param recordCache 逻辑消息缓存
     */
    private void enqueueCallback(MessageReceiptPo receipt, MessageDeliveryPo delivery, DeliveryStatus target,
                                 LocalDateTime receiptTime, DeliveryReceiptItem item, ReceiptApplyResult result,
                                 Map<Long, MessageRecordPo> recordCache) {
        MessageRecordPo record = loadRecord(delivery.getMessageId(), recordCache);
        if (record == null || !StringUtils.hasText(record.getCallbackUrl())) {
            return;
        }
        MessageCallbackTaskPo task = new MessageCallbackTaskPo();
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
    private static Map<String, Object> buildCallbackPayload(MessageReceiptPo receipt, MessageDeliveryPo delivery,
                                                            MessageRecordPo record, DeliveryStatus target,
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
     * 按回执结果批量重算逻辑消息状态：一次查询取回全部相关投递，按消息分组后逐条汇总。
     *
     * <p>聚合口径与发送/重试共用 {@link MessageStatusAggregator}，避免回执推进与重试重算
     * 对同一条消息得出不同状态。</p>
     *
     * @param messageIds  本次推进过的逻辑消息ID集合
     * @param recordCache 逻辑消息缓存
     */
    private void refreshRecordStatuses(Set<Long> messageIds, Map<Long, MessageRecordPo> recordCache) {
        if (messageIds.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<MessageDeliveryPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(MessageDeliveryPo::getMessageId, messageIds)
                .orderByAsc(MessageDeliveryPo::getId);
        Map<Long, List<MessageDeliveryPo>> deliveriesByMessage = deliveryMapper.selectList(wrapper).stream()
                .collect(Collectors.groupingBy(MessageDeliveryPo::getMessageId));
        for (Long messageId : messageIds) {
            MessageRecordPo record = loadRecord(messageId, recordCache);
            if (record == null) {
                continue;
            }
            String next = MessageStatusAggregator.aggregate(
                    deliveriesByMessage.getOrDefault(messageId, List.of())).name();
            if (next.equals(record.getStatus())) {
                continue;
            }
            record.setStatus(next);
            recordMapper.updateById(record);
        }
    }

    /**
     * 加载逻辑消息并写入本批缓存。
     *
     * @param messageId   逻辑消息ID
     * @param recordCache 逻辑消息缓存
     * @return 逻辑消息；不存在时为 {@code null}
     */
    private MessageRecordPo loadRecord(Long messageId, Map<Long, MessageRecordPo> recordCache) {
        if (messageId == null) {
            return null;
        }
        if (recordCache.containsKey(messageId)) {
            return recordCache.get(messageId);
        }
        MessageRecordPo record = recordMapper.selectById(messageId);
        recordCache.put(messageId, record);
        return record;
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
