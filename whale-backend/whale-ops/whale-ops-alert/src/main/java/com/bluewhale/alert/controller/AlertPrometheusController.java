package com.bluewhale.alert.controller;

import com.bluewhale.alert.config.AlertNotifyProperties;
import com.bluewhale.alert.dto.PrometheusWebhookPayload;
import com.bluewhale.alert.service.AlertRecordService;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Prometheus Alertmanager 入站告警接口。
 *
 * <p>路径位于 {@code /alert/v1/internal/**}，网关对该前缀统一 403，只允许内网
 * Alertmanager 直连 whale-alert 服务；同时校验 {@code X-Alert-Token} 与
 * {@code whale.alert.notify.webhook-token} 一致。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@RestController
@RequestMapping("/alert/v1/internal")
@RequiredArgsConstructor
public class AlertPrometheusController {

    /** 入站校验请求头 */
    private static final String TOKEN_HEADER = "X-Alert-Token";

    /** Bearer 前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    private final AlertRecordService alertRecordService;

    private final AlertNotifyProperties properties;

    /**
     * 接收 Alertmanager Webhook 告警
     *
     * <p>令牌既可放在 {@code X-Alert-Token}，也可用 Alertmanager 原生
     * {@code Authorization: Bearer <token>} 形式传递。</p>
     *
     * @param token         入站令牌请求头
     * @param authorization Bearer 授权头
     * @param payload       Alertmanager 请求体
     * @param request       HTTP 请求（仅用于失败时记录来源地址）
     * @return 落库条数
     */
    @PostMapping("/prometheus")
    public Result<Integer> receive(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                   @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                                   String authorization,
                                   @RequestBody PrometheusWebhookPayload payload,
                                   HttpServletRequest request) {
        String candidate = StringUtils.hasText(token) ? token : stripBearer(authorization);
        if (!tokenMatches(candidate)) {
            log.warn("Alertmanager 入站令牌校验失败: remoteAddr={}",
                    request == null ? "-" : request.getRemoteAddr());
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        int inserted = alertRecordService.ingest(payload);
        log.info("Alertmanager 入站告警处理完成: inserted={}", inserted);
        return Result.ok(inserted);
    }

    /**
     * 去掉 Bearer 前缀。
     *
     * @param authorization 授权头
     * @return 裸令牌；无授权头时为 {@code null}
     */
    private static String stripBearer(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        return authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length()).trim()
                : authorization.trim();
    }

    /**
     * 常量时间比较入站令牌与配置令牌。
     *
     * @param token 请求头令牌
     * @return 是否匹配
     */
    private boolean tokenMatches(String token) {
        String expected = properties.getWebhookToken();
        if (!StringUtils.hasText(expected) || token == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }
}
