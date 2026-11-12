package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.response.GatewayBlacklistItemResponse;
import com.bluewhale.common.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 网关黑名单内网 API 契约：仅供 whale-gateway 定时拉取，不对外暴露。
 *
 * <p>黑名单优先于白名单：网关先匹配本接口下发的规则，命中即 403。
 * 路径以 {@code /auth/v1/internal} 开头：网关 internal-deny 统一 403 外部访问，
 * 服务侧由 {@code InternalTokenFilter} 校验 {@code X-Internal-Token}。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/auth/v{version}/internal/gateway", version = "1")
public interface GatewayBlacklistInternalApi {

    /**
     * 查询全部生效的平台级黑名单
     *
     * @return 黑名单项列表（按 {@code sort_no} 升序）；无封禁时为空列表
     */
    @GetMapping("/blacklist")
    Result<List<GatewayBlacklistItemResponse>> listEnabled();
}
