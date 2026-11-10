package com.bluewhale.contract.controller;

import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.contract.service.EsignCallbackService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 第三方电子签章回调接口。
 *
 * <p>服务内路径 {@code /contract/sign/callback/{provider}}；回调由三方平台匿名调用，
 * 使用渠道 HMAC/RSA 验签 + 时间戳窗口 + 事件唯一键做安全与幂等控制，
 * 因此不加权限码，但在网关 JWT 白名单中仅放行该前缀。所有回调（含验签失败）留审计。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/contract/sign/callback")
@RequiredArgsConstructor
public class EsignCallbackController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "ct_esign_callback";

    private final EsignCallbackService esignCallbackService;

    /**
     * 接收三方签署结果回调。
     *
     * @param provider    渠道名（local/esignbao/fadada/ssq）
     * @param rawBody     回调原始报文
     * @param httpRequest 当前请求（读取验签头）
     * @return 空结果
     */
    @PostMapping("/{provider}")
    @AuditLog(operation = "电子签章回调", type = OperateType.UPDATE,
            targetType = TARGET_TYPE, targetId = "#provider")
    public Result<Void> callback(@PathVariable String provider, @RequestBody String rawBody,
                                 HttpServletRequest httpRequest) {
        esignCallbackService.handle(provider, rawBody, headers(httpRequest));
        return Result.ok();
    }

    /**
     * 复制请求头（大小写保持原样，由渠道实现做大小写不敏感匹配）。
     *
     * @param request 当前请求
     * @return 请求头映射
     */
    private static Map<String, String> headers(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        var names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }
}
