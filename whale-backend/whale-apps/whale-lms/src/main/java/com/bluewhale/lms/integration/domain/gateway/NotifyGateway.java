package com.bluewhale.lms.integration.domain.gateway;

import com.bluewhale.lms.integration.domain.model.NotifyRequest;

/**
 * 通知投递网关。
 *
 * <p>投递失败直接抛异常，由应用层记为 {@code FAILED} 并留待下一轮重试。
 * 网关不做吞异常式的「尽力而为」：静默失败会让催办看起来发了、实际没人收到。</p>
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
