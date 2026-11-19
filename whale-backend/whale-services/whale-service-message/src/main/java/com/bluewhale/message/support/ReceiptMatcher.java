package com.bluewhale.message.support;

import com.bluewhale.message.dto.DeliveryReceiptItem;
import com.bluewhale.message.enums.MessageChannel;
import com.bluewhale.message.po.MessageDeliveryPo;

import java.util.List;
import java.util.Map;

/**
 * 回执匹配策略（可插拔）。
 *
 * <p>受理入口按 {@link #order()} 升序依次调用：前序策略已命中的回执不再参与后续匹配，
 * 从而在"精确匹配优先、兜底匹配其次"的前提下保持批量查询性能。</p>
 *
 * <p>实现必须在当前 {@code TenantContext} 下查询，依赖租户插件自动追加 {@code tenant_id} 条件，
 * 不得返回其它租户的投递记录。</p>
 *
 * @author BlueWhale
 */
public interface ReceiptMatcher {

    /**
     * 匹配优先级，数值越小越先执行。
     *
     * @return 顺序值
     */
    int order();

    /**
     * 是否支持该渠道。
     *
     * @param channel 渠道
     * @return 支持返回 {@code true}
     */
    boolean supports(MessageChannel channel);

    /**
     * 批量匹配投递记录。
     *
     * @param items 待匹配的回执
     * @return 命中项到投递记录的映射；未命中的回执不出现在结果中
     */
    Map<DeliveryReceiptItem, List<MessageDeliveryPo>> match(List<DeliveryReceiptItem> items);
}
