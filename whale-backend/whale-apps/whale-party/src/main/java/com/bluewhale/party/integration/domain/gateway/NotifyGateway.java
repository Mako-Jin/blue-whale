package com.bluewhale.party.integration.domain.gateway;

import com.bluewhale.party.integration.domain.model.NotifyRequest;

/**
 * 通知投递网关。
 *
 * <p>失败直接抛异常，由应用层记为 FAILED 并留待下一轮重试。</p>
 *
 * @author BlueWhale
 */
public interface NotifyGateway {

    /**
     * 投递一条通知。
     *
     * @param request 通知请求
     */
    void send(NotifyRequest request);
}
