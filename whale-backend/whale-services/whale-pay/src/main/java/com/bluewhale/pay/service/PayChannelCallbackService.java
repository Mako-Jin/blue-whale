package com.bluewhale.pay.service;

import java.util.Map;

/**
 * 渠道异步回调入站服务。
 *
 * <p>处理顺序固定为：验签解析 → 幂等留痕 → 反查租户与支付单 → 金额币种校验 →
 * 复用 {@link PaySettlementService} 统一落账 → 回写日志。回调请求不信任
 * {@code X-Internal-Token} 与 {@code X-Tenant-Id}，仅凭渠道验签与支付单反查确定租户。</p>
 *
 * @author BlueWhale
 */
public interface PayChannelCallbackService {

    /**
     * 处理一次渠道回调，返回渠道要求的响应体（HTTP 恒为 200）。
     *
     * @param channel 渠道编码（路径参数）
     * @param rawBody 原始报文
     * @param headers 原始请求头
     * @return 渠道成功或失败响应体
     */
    String handle(String channel, String rawBody, Map<String, String> headers);
}
