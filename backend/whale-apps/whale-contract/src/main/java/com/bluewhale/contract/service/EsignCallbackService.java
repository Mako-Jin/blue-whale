package com.bluewhale.contract.service;

import java.util.Map;

/**
 * 电子签章回调服务。
 *
 * @author BlueWhale
 */
public interface EsignCallbackService {

    /**
     * 处理三方电子签章回调：验签 → 幂等 → 证据落库 → 推进流程。
     *
     * @param provider 渠道名（回调路径参数）
     * @param rawBody  回调原始报文
     * @param headers  请求头
     */
    void handle(String provider, String rawBody, Map<String, String> headers);
}
