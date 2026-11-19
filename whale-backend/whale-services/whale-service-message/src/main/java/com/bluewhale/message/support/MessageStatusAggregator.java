package com.bluewhale.message.support;

import com.bluewhale.message.po.MessageDeliveryPo;
import com.bluewhale.message.enums.DeliveryStatus;
import com.bluewhale.message.enums.MessageStatus;

import java.util.List;

/**
 * 消息状态聚合器：把一条消息下的投递状态汇总为消息状态。
 *
 * <p>发送、重试（提交态）与回执推进（送达态）共用同一口径，避免两套算法对同一条消息
 * 得出不同状态：</p>
 * <ul>
 *   <li>全部 FAILED，或全部为 FAILED/BOUNCED —— {@link MessageStatus#FAILED}；</li>
 *   <li>存在任一新异常（FAILED/BOUNCED）且非全异常 —— {@link MessageStatus#PARTIAL}；</li>
 *   <li>无异常但仍有 SENT 在途 —— {@link MessageStatus#SENT}（不提前宣称已送达）；</li>
 *   <li>无异常、无在途、存在 DELIVERED/READ —— {@link MessageStatus#DELIVERED}；
 *       SKIPPED（渠道主动跳过）视为正常，不影响该结论；</li>
 *   <li>全部 EXPIRED —— {@link MessageStatus#EXPIRED}；</li>
 *   <li>全部 MOCKED —— {@link MessageStatus#MOCKED}；</li>
 *   <li>其余（含全部 SKIPPED）—— {@link MessageStatus#SKIPPED}。</li>
 * </ul>
 *
 * <p>历史缺陷：旧实现只统计 FAILED/SENT/MOCKED，回执推进后（投递已是 DELIVERED/READ）
 * 重试重算会落到 SKIPPED；BOUNCED 也未计入异常。此实现修正该口径。</p>
 *
 * @author BlueWhale
 */
public final class MessageStatusAggregator {

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private MessageStatusAggregator() {
    }

    /**
     * 聚合消息状态。
     *
     * @param deliveries 该消息下的全部投递记录
     * @return 聚合后的消息状态
     */
    public static MessageStatus aggregate(List<MessageDeliveryPo> deliveries) {
        if (deliveries == null || deliveries.isEmpty()) {
            return MessageStatus.SKIPPED;
        }
        long total = deliveries.size();
        long failed = count(deliveries, DeliveryStatus.FAILED);
        long bounced = count(deliveries, DeliveryStatus.BOUNCED);
        long delivered = count(deliveries, DeliveryStatus.DELIVERED) + count(deliveries, DeliveryStatus.READ);
        long pending = count(deliveries, DeliveryStatus.SENT);
        long mocked = count(deliveries, DeliveryStatus.MOCKED);
        long expired = count(deliveries, DeliveryStatus.EXPIRED);
        long abnormal = failed + bounced;
        if (abnormal == total) {
            return MessageStatus.FAILED;
        }
        if (abnormal > 0) {
            return MessageStatus.PARTIAL;
        }
        if (expired == total) {
            return MessageStatus.EXPIRED;
        }
        if (pending > 0) {
            return MessageStatus.SENT;
        }
        if (delivered > 0) {
            return MessageStatus.DELIVERED;
        }
        if (mocked == total) {
            return MessageStatus.MOCKED;
        }
        return MessageStatus.SKIPPED;
    }

    /**
     * 统计指定状态的投递条数。
     *
     * @param deliveries 投递记录
     * @param status     目标状态
     * @return 条数
     */
    private static long count(List<MessageDeliveryPo> deliveries, DeliveryStatus status) {
        String name = status.name();
        return deliveries.stream()
                .filter(delivery -> name.equals(delivery.getStatus()))
                .count();
    }
}
