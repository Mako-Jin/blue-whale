package com.bluewhale.pay.controller;

import com.bluewhale.pay.service.PayChannelCallbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 渠道异步回调入站接口。
 *
 * <p>外部路径 {@code POST /pay/v1/callback/{channel}}，网关白名单放行并剥离入站身份/租户头。
 * 本接口<b>不校验</b> {@code X-Internal-Token}、<b>不信任</b> {@code X-Tenant-Id}：
 * 安全完全依赖渠道适配器内的验签，租户由支付单反查。响应 HTTP 恒为 200，
 * 由渠道按响应体判断是否重发。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/pay/v{version}/callback", version = "1")
@RequiredArgsConstructor
public class PayChannelCallbackController {

    private final PayChannelCallbackService payChannelCallbackService;

    /**
     * 接收渠道回调。
     *
     * @param channel 渠道编码（路径参数）
     * @param rawBody 原始报文（不同渠道 Content-Type 不同，按字符串接收）
     * @param headers 原始请求头
     * @return 渠道要求的成功或失败响应体（HTTP 200）
     */
    @PostMapping("/{channel}")
    public ResponseEntity<String> callback(@PathVariable String channel,
                                           @RequestBody(required = false) String rawBody,
                                           @RequestHeader Map<String, String> headers) {
        return ResponseEntity.ok(payChannelCallbackService.handle(channel, rawBody, headers));
    }
}
