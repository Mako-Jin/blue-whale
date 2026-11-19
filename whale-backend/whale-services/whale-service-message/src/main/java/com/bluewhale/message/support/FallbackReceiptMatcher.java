package com.bluewhale.message.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.message.dto.DeliveryReceiptItem;
import com.bluewhale.message.enums.MessageChannel;
import com.bluewhale.message.mapper.MessageDeliveryMapper;
import com.bluewhale.message.po.MessageDeliveryPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 兜底匹配策略：供应商未回传消息标识时，按「渠道 + 接收人 + 发送时间窗」匹配。
 *
 * <p>只在回执未携带 {@code providerMessageId}、且渠道不支持精确匹配时生效。为降低误归因，
 * 匹配范围限定为：</p>
 * <ul>
 *   <li>同一渠道、同一接收人；</li>
 *   <li>投递记录尚未绑定任何供应商标识（{@code provider_message_id IS NULL}），
 *       已被精确匹配占用的投递不会被兜底瓜分；</li>
 *   <li>发送时间与回执时间相差不超过 {@link #WINDOW}，多条命中时取时间最接近的一条。</li>
 * </ul>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class FallbackReceiptMatcher implements ReceiptMatcher {

    /** 兜底匹配优先级最低，排在精确匹配之后 */
    private static final int ORDER = 100;

    /** 允许的发送/回执时间差 */
    private static final Duration WINDOW = Duration.ofMinutes(5);

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
        Map<String, List<DeliveryReceiptItem>> itemsByChannel = groupCandidates(items);
        Map<DeliveryReceiptItem, List<MessageDeliveryPo>> result = new HashMap<>();
        for (Map.Entry<String, List<DeliveryReceiptItem>> entry : itemsByChannel.entrySet()) {
            List<DeliveryReceiptItem> channelItems = entry.getValue();
            List<MessageDeliveryPo> candidates = loadCandidates(entry.getKey(), channelItems);
            for (DeliveryReceiptItem item : channelItems) {
                MessageDeliveryPo nearest = nearestCandidate(item, candidates);
                if (nearest != null) {
                    result.put(item, List.of(nearest));
                }
            }
        }
        return result;
    }

    /**
     * 收集可兜底匹配的回执：无供应商标识、有接收人与回执时间。
     *
     * @param items 待匹配回执
     * @return 渠道到回执列表
     */
    private static Map<String, List<DeliveryReceiptItem>> groupCandidates(List<DeliveryReceiptItem> items) {
        Map<String, List<DeliveryReceiptItem>> result = new HashMap<>();
        for (DeliveryReceiptItem item : items) {
            if (StringUtils.hasText(item.getProviderMessageId())
                    || !StringUtils.hasText(item.getRecipient())
                    || item.getReceiptTime() == null) {
                continue;
            }
            MessageChannel channel = MessageChannel.parse(item.getChannel());
            if (channel == null) {
                continue;
            }
            result.computeIfAbsent(channel.name(), key -> new ArrayList<>()).add(item);
        }
        return result;
    }

    /**
     * 查询同渠道、同接收人、未绑定供应商标识且落在时间窗内的投递记录。
     *
     * @param channel     渠道
     * @param channelItems 渠道内的待匹配回执
     * @return 候选投递记录
     */
    private List<MessageDeliveryPo> loadCandidates(String channel, List<DeliveryReceiptItem> channelItems) {
        Set<String> recipients = new HashSet<>();
        LocalDateTime minTime = null;
        LocalDateTime maxTime = null;
        for (DeliveryReceiptItem item : channelItems) {
            recipients.add(item.getRecipient().trim());
            LocalDateTime receiptTime = item.getReceiptTime();
            minTime = minTime == null || receiptTime.isBefore(minTime) ? receiptTime : minTime;
            maxTime = maxTime == null || receiptTime.isAfter(maxTime) ? receiptTime : maxTime;
        }
        if (recipients.isEmpty() || minTime == null) {
            return List.of();
        }
        LambdaQueryWrapper<MessageDeliveryPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageDeliveryPo::getChannel, channel)
                .in(MessageDeliveryPo::getRecipient, recipients)
                .isNull(MessageDeliveryPo::getProviderMessageId)
                .ge(MessageDeliveryPo::getSentAt, minTime.minus(WINDOW))
                .le(MessageDeliveryPo::getSentAt, maxTime.plus(WINDOW))
                .orderByAsc(MessageDeliveryPo::getId);
        return deliveryMapper.selectList(wrapper);
    }

    /**
     * 在候选中选取发送时间最接近回执时间的一条。
     *
     * @param item       回执
     * @param candidates 候选投递记录
     * @return 最近的投递记录；无满足条件的候选时为 {@code null}
     */
    private static MessageDeliveryPo nearestCandidate(DeliveryReceiptItem item,
                                                      List<MessageDeliveryPo> candidates) {
        String recipient = item.getRecipient().trim();
        MessageDeliveryPo nearest = null;
        long nearestMillis = Long.MAX_VALUE;
        for (MessageDeliveryPo candidate : candidates) {
            if (candidate.getSentAt() == null || !recipient.equals(candidate.getRecipient())) {
                continue;
            }
            long distance = Math.abs(Duration.between(candidate.getSentAt(), item.getReceiptTime()).toMillis());
            if (distance <= WINDOW.toMillis() && distance < nearestMillis) {
                nearest = candidate;
                nearestMillis = distance;
            }
        }
        return nearest;
    }
}
