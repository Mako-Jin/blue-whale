package com.bluewhale.message.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.message.dto.DeliveryReceiptItem;
import com.bluewhale.message.enums.MessageChannel;
import com.bluewhale.message.mapper.MessageDeliveryMapper;
import com.bluewhale.message.po.MessageDeliveryPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 精确匹配策略：按「渠道 + provider_message_id」命中投递记录。
 *
 * <p>各渠道发送时都会回写供应商标识（EMAIL 的 {@code Message-ID}、SMS 网关消息ID、
 * IM 的 {@code clientMsgId}、INAPP 的平台消息ID），因此该策略是首选匹配方式。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class ProviderIdReceiptMatcher implements ReceiptMatcher {

    /** 精确匹配优先级最高 */
    private static final int ORDER = 0;

    private final MessageDeliveryMapper deliveryMapper;

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public boolean supports(MessageChannel channel) {
        return true;
    }

    @Override
    public Map<DeliveryReceiptItem, List<MessageDeliveryPo>> match(List<DeliveryReceiptItem> items) {
        Map<String, Set<String>> idsByChannel = new HashMap<>();
        for (DeliveryReceiptItem item : items) {
            if (!StringUtils.hasText(item.getProviderMessageId())) {
                continue;
            }
            String channel = normalize(item.getChannel());
            if (channel == null) {
                continue;
            }
            idsByChannel.computeIfAbsent(channel, key -> new LinkedHashSet<>())
                    .add(item.getProviderMessageId().trim());
        }
        Map<String, List<MessageDeliveryPo>> deliveriesByKey = loadDeliveries(idsByChannel);
        Map<DeliveryReceiptItem, List<MessageDeliveryPo>> result = new HashMap<>();
        for (DeliveryReceiptItem item : items) {
            String channel = normalize(item.getChannel());
            if (channel == null || !StringUtils.hasText(item.getProviderMessageId())) {
                continue;
            }
            List<MessageDeliveryPo> deliveries = deliveriesByKey.get(
                    matchKey(channel, item.getProviderMessageId()));
            if (deliveries != null && !deliveries.isEmpty()) {
                result.put(item, deliveries);
            }
        }
        return result;
    }

    /**
     * 按渠道分组批量查询投递记录。
     *
     * @param idsByChannel 渠道到提供方消息标识集合
     * @return 键为「渠道|标识」的投递记录分组
     */
    private Map<String, List<MessageDeliveryPo>> loadDeliveries(Map<String, Set<String>> idsByChannel) {
        Map<String, List<MessageDeliveryPo>> result = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : idsByChannel.entrySet()) {
            LambdaQueryWrapper<MessageDeliveryPo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MessageDeliveryPo::getChannel, entry.getKey())
                    .in(MessageDeliveryPo::getProviderMessageId, entry.getValue())
                    .orderByAsc(MessageDeliveryPo::getId);
            for (MessageDeliveryPo delivery : deliveryMapper.selectList(wrapper)) {
                result.computeIfAbsent(matchKey(entry.getKey(), delivery.getProviderMessageId()),
                        key -> new ArrayList<>()).add(delivery);
            }
        }
        return result;
    }

    /**
     * 归一化渠道编码。
     *
     * @param raw 原始渠道文本
     * @return 渠道名；无法识别时为 {@code null}
     */
    private static String normalize(String raw) {
        MessageChannel channel = MessageChannel.parse(raw);
        return channel == null ? null : channel.name();
    }

    /**
     * 构造匹配键。
     *
     * @param channel           渠道
     * @param providerMessageId 提供方消息标识
     * @return 匹配键
     */
    private static String matchKey(String channel, String providerMessageId) {
        return channel + "|" + providerMessageId.trim();
    }
}
