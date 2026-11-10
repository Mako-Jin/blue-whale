package com.bluewhale.crm.marketing.domain.port;

import com.bluewhale.crm.marketing.domain.model.MarketingMessage;

/**
 * whale-message 消息投递端口。
 *
 * <p>实现方不得伪造成功：服务未启用或调用失败时必须返回 {@code FAILED}。</p>
 *
 * @author BlueWhale
 */
public interface MessageDispatchPort {

    /**
     * 投递单条营销消息。
     *
     * @param message 营销消息
     * @return 投递结果
     */
    MessageDispatchResult dispatch(MarketingMessage message);
}
